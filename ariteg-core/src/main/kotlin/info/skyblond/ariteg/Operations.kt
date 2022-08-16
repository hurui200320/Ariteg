package info.skyblond.ariteg

import info.skyblond.ariteg.slicers.SlicerProvider
import info.skyblond.ariteg.storage.Storage
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import kotlin.math.min

object Operations {
    private val logger = KotlinLogging.logger("Operations")
    private const val MEGA_BYTE = 1024 * 1024
    private fun format(number: Double): String {
        return "%.2f".format(number)
    }

    fun getLimitingSemaphore(): Semaphore =
        Semaphore(Runtime.getRuntime().availableProcessors() * 2)

    /**
     * Digest the [root] file, slice it and save as an [Entry]
     * */
    @JvmStatic
    fun digest(root: File, slicerProvider: SlicerProvider, storage: Storage): Entry {
        val rootLink = internalDigest(root, slicerProvider, storage)
        val entry = Entry(root.name, rootLink.get(), Date(), "")
        logger.info { "Writing entry for ${root.canonicalPath}" }
        return storage.addEntry(entry).get()
    }

    private fun internalDigest(
        file: File,
        slicerProvider: SlicerProvider,
        storage: Storage
    ): CompletableFuture<Link> =
        when {
            file.isFile -> CompletableFuture.supplyAsync {
                val slicer = slicerProvider.invoke(file)
                val totalSize = file.length().toDouble()
                val blobIterator = slicer.iterator()
                // use semaphore to limit memory usage
                // aka how many pending blobs in memory
                val semaphore = getLimitingSemaphore()
                logger.info { "Slicing chunks: ${file.canonicalPath}" }
                val futureLinks = LinkedList<CompletableFuture<Link>>()
                var processedSize = 0L
                var lastTime = 0L
                // slice and store blobs
                while (blobIterator.hasNext()) {
                    semaphore.acquire()
                    val nextBlob = blobIterator.next()
                    processedSize += nextBlob.data.size
                    futureLinks.add(
                        storage.write(Link.Type.BLOB, nextBlob)
                            .exceptionally { semaphore.release(); logger.error(it) { "Error when writing" }; throw it }
                            .thenApply { semaphore.release(); it }
                    )
                    // print processing info
                    if (System.currentTimeMillis() - lastTime >= 1000) {
                        logger.info {
                            "[${format(processedSize * 100.0 / totalSize)}%]" +
                                    "Processed ${format(processedSize / MEGA_BYTE.toDouble())}MB " +
                                    "of ${format(totalSize / MEGA_BYTE)}MB (${file.canonicalPath})"
                        }
                        lastTime = System.currentTimeMillis()
                    }
                }

                if (blobIterator is AutoCloseable) {
                    blobIterator.close()
                }

                logger.info { "Reconstructing structural info: ${file.canonicalPath}" }
                // make links into ListObjects
                var i = 0
                val listLength = 128
                while (futureLinks.size > 1) {
                    // get some link
                    val listSize = min(listLength, futureLinks.size - i)
                    val list = LinkedList<CompletableFuture<Link>>()
                    for (j in 0 until listSize) {
                        list.add(futureLinks.removeAt(i))
                    }
                    // make it a list
                    val listObject = ListObject(list.map { it.get() })
                    val receipt = storage.write(Link.Type.LIST, listObject)
                    logger.info { "Reconstructing ${file.canonicalPath}: ${futureLinks.size} left" }
                    // update i (next fetch point at next iter)
                    futureLinks.add(i++, receipt)
                    // reset i if remained links are not enough for a new list
                    if (i + listLength > futureLinks.size) {
                        i = 0
                    }
                }

                // in case the file is empty, slicer return nothing
                if (futureLinks.size == 0) {
                    futureLinks.add(storage.write(Link.Type.BLOB, Blob(ByteArray(0))))
                }
                logger.info { "Finish uploading ${file.canonicalPath}" }
                // The last one should be the root
                futureLinks[0].get().copy(name = file.name)
            }

            file.isDirectory -> CompletableFuture.supplyAsync {
                val files = file.listFiles() ?: error("List folder returns null")
                val subLinks = files.map { f ->
                    // sync op here, the internal digest of file is already async
                    internalDigest(f, slicerProvider, storage).thenApply {
                        it.copy(name = f.name)
                    }.get()
                }
                TreeObject(subLinks).let {
                    storage.write(Link.Type.TREE, it).get().copy(name = file.name)
                }
            }

            else -> error("Bad file: ${file.canonicalPath}")
        }


    /**
     * Get all [Blob] links related to the given [entry].
     * By design, non-blob objects should not be archived and can be accessed instantly.
     * */
    @JvmStatic
    fun resolve(entry: Entry, storage: Storage): Set<Link> {
        return storage.resolve(entry.link).get()
    }

    /**
     * Reconstruct the whole content according to the given [entry].
     * The blobs must be recovered first.
     * */
    @JvmStatic
    fun restore(entry: Entry, storage: Storage, root: File) {
        if (root.exists()) {
            check(root.isDirectory) { "Root folder is a file: ${root.canonicalPath}" }
        } else {
            check(root.mkdirs()) { "Failed to create root folder: ${root.canonicalPath}" }
        }
        internalRestore(entry.link, storage, File(root, entry.name))
    }

    private fun internalRestore(link: Link, storage: Storage, root: File) {
        logger.info { "Restoring ${root.canonicalPath}" }
        when (link.type) {
            Link.Type.BLOB -> {
                val blob = storage.read(link).get() as Blob
                FileUtils.writeByteArrayToFile(root, blob.data)
            }

            Link.Type.LIST -> {
                val links = LinkedList<Link>()
                (storage.read(link).get() as ListObject).also {
                    links.addAll(it.content)
                }

                FileUtils.openOutputStream(root).use {
                    while (links.isNotEmpty()) {
                        val l = links.pollFirst()
                        when (l.type) {
                            Link.Type.BLOB -> {
                                val blob = storage.read(l).get() as Blob
                                it.write(blob.data)
                            }

                            Link.Type.LIST -> {
                                val listObj = storage.read(l).get() as ListObject
                                links.addAll(0, listObj.content)
                            }

                            else -> error("Illegal type in list: ${l.type}")
                        }
                    }
                }
            }

            Link.Type.TREE -> {
                val treeObj = storage.read(link).get() as TreeObject
                if (root.exists()) {
                    check(root.isDirectory) { "Root folder is a file: ${root.canonicalPath}" }
                } else {
                    check(root.mkdirs()) { "Failed to create root folder: ${root.canonicalPath}" }
                }
                for (l in treeObj.content) {
                    internalRestore(l, storage, File(root, l.name ?: l.hash))
                }
            }
        }
    }

    /**
     * List all existing entries
     * */
    @JvmStatic
    fun listEntry(storage: Storage): List<Entry> {
        return storage.listEntry().toList()
    }

    /**
     * Delete entry
     * */
    @JvmStatic
    fun deleteEntry(entry: Entry, storage: Storage) {
        storage.removeEntry(entry.id).get()
    }

    /**
     * Delete all unreachable objects
     * */
    @JvmStatic
    fun gc(storage: Storage) {
        val listObjectsFuture = storage.listObjects()
        val workingQueue = LinkedList<Link>()
        logger.info { "Listing entries..." }
        workingQueue.addAll(storage.listEntry().map { it.link })
        logger.info { "Listed ${workingQueue.size} entries" }
        val waitingQueue = LinkedList<CompletableFuture<out AritegObject>>()
        logger.info { "Listing current objects..." }
        // get all objects and remove reachable objects
        val (blobs, lists, trees) = listObjectsFuture.get()
            .let { Triple(it.first.toMutableSet(), it.second.toMutableSet(), it.third.toMutableSet()) }
        logger.info { "Listed ${blobs.size} blobs, ${lists.size} lists, and ${trees.size} trees" }
        while (workingQueue.isNotEmpty()) {
            val link = workingQueue.poll()!!
            when (link.type) {
                Link.Type.BLOB -> {
                    blobs.remove(link.hash)
                }

                Link.Type.LIST -> {
                    if (lists.contains(link.hash)) {
                        // the sub links are not visited yet
                        // submit the job and waiting
                        waitingQueue.add(storage.read(link))
                        lists.remove(link.hash)
                    } // otherwise, the link and its sub links are visited
                }

                Link.Type.TREE -> {
                    if (trees.contains(link.hash)) {
                        waitingQueue.add(storage.read(link))
                        trees.remove(link.hash)
                    }
                }
            }

            if (workingQueue.isEmpty()) {
                // get result from waiting queue and put into working queue
                logger.info { "${blobs.size} blobs remain, ${lists.size} lists remain, ${trees.size} trees remain" }
                logger.info { "${waitingQueue.size} objects are fetching..." }
                waitingQueue.forEach {
                    when (val obj = it.get()) {
                        is ListObject -> workingQueue.addAll(obj.content)
                        is TreeObject -> workingQueue.addAll(obj.content)
                    }
                }
                waitingQueue.clear()
                logger.info { "Checking ${workingQueue.size} objects..." }
            }
        }
        logger.info { "Found ${blobs.size} unreachable blobs, ${lists.size} unreachable lists, and ${trees.size} unreachable trees" }
        // now what we left are unreachable objects
        val deletingQueue = LinkedList<CompletableFuture<Void>>()
        blobs.forEach { deletingQueue.add(storage.delete(Link(it, Link.Type.BLOB))) }
        lists.forEach { deletingQueue.add(storage.delete(Link(it, Link.Type.LIST))) }
        trees.forEach { deletingQueue.add(storage.delete(Link(it, Link.Type.TREE))) }
        deletingQueue.forEach { it.get() }
    }

    @JvmStatic
    fun integrityCheck(storage: Storage, deleting: Boolean = false) {
        val listObjectsFuture = storage.listObjects()
        logger.info { "Listing current objects..." }
        // get all objects and remove reachable objects
        val blobs = listObjectsFuture.get().first
        logger.info { "Listed ${blobs.size} blobs" }
        val semaphore = getLimitingSemaphore()
        val brokenList = blobs.mapIndexed { index, hash ->
            val link = Link(hash, Link.Type.BLOB)
            semaphore.acquire()
            if (index % 100 == 0) {
                logger.info { "Checking ${index / 1000.0}K blobs" }
            }
            storage.read(link)
                .thenApply { semaphore.release(); null as Link? }
                .exceptionally {
                    semaphore.release()
                    if (it is AritegObject.HashNotMatchException) {
                        // exception means the hash is not correct
                        logger.info { "Hash not match on ${link.hash}, deleting..." }
                        link
                    } else {
                        throw it
                    }
                }
        }.mapNotNull { it.get() }
        logger.info { "Found ${brokenList.size} broken blobs:" }
        brokenList.forEach {
            logger.info { "(${it.type})${it.hash}" }
        }
        if (deleting) {
            logger.info { "Deleting broken blobs..." }
            brokenList
                .map { storage.delete(it) }
                .forEach { it.get() }
        }

    }
}

