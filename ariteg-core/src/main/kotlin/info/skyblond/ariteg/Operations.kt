package info.skyblond.ariteg

import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.math.min

object Operations {
    private val logger = KotlinLogging.logger("Operations")
    private const val MEGA_BYTE = 1024 * 1024
    private fun format(number: Double): String {
        return "%.2f".format(number)
    }

    /**
     * Return how many memory we can use
     * */
    fun getFreeMemory(): Long = Runtime.getRuntime().freeMemory()

    fun getMemoryLowWaterMark(): Long = (Runtime.getRuntime().totalMemory() * 0.3).toLong()

    /**
     * Digest the [root] file, slice it and save as an [Entry]
     * */
    @JvmStatic
    suspend fun digest(root: File, slicer: Slicer, storage: Storage): Entry {
        val rootLink = internalDigest(root, slicer, storage)
        val entry = Entry(root.name, rootLink.await(), Date(), "")
        logger.info { "Writing entry for ${root.canonicalPath}" }
        return storage.addEntry(entry)
    }

    private suspend fun internalDigest(
        file: File,
        slicer: Slicer,
        storage: Storage
    ): Deferred<Link> = coroutineScope {
        when {
            file.isFile -> async {
                val totalSize = file.length().toDouble()
                val futureLinks = LinkedList<Deferred<Link>>()
                logger.info { "Slicing chunks: ${file.canonicalPath}" }
                var processedSize = 0L
                var lastTime = 0L

                withContext(Dispatchers.IO) {
                    Files.newInputStream(file.toPath(), StandardOpenOption.READ)
                }.use { fis ->
                    slicer.slice(fis).let { seq ->
                        while (true) {
                            // read as many as possible
                            val list = seq.takeWhile { getFreeMemory() >= getMemoryLowWaterMark() }
                                .toList()
                            // write one by one
                            list.forEach { nextBlob ->
                                processedSize += nextBlob.data.size
                                futureLinks.add(async {
                                    storage.write(Link.Type.BLOB, nextBlob)
                                })
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
                            if (list.isEmpty()) {
                                break
                            }
                        }
                    }
                }

                logger.info { "Reconstructing structural info: ${file.canonicalPath}" }
                // make links into ListObjects
                var i = 0
                val listLength = 128
                while (futureLinks.size > 1) {
                    // get some link
                    val listSize = min(listLength, futureLinks.size - i)
                    val list = LinkedList<Deferred<Link>>()
                    for (j in 0 until listSize) {
                        list.add(futureLinks.removeAt(i))
                    }
                    // make it a list
                    val listObject = ListObject(list.map { it.await() })
                    val receipt = async { storage.write(Link.Type.LIST, listObject) }
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
                    futureLinks.add(async { storage.write(Link.Type.BLOB, Blob(ByteArray(0))) })
                }
                logger.info { "Finish uploading ${file.canonicalPath}" }
                // The last one should be the root
                futureLinks[0].await().copy(name = file.name)
            }

            file.isDirectory -> async {
                val files = file.listFiles() ?: error("List folder returns null")
                val subLinks = files.map { f ->
                    // sync op here, the internal digest of file is already async
                    internalDigest(f, slicer, storage).await().copy(name = f.name)
                }
                storage.write(Link.Type.TREE, TreeObject(subLinks)).copy(name = file.name)
            }

            else -> error("Bad file: ${file.canonicalPath}")
        }
    }


    /**
     * Get all [Blob] links related to the given [entry].
     * By design, non-blob objects should not be archived and can be accessed instantly.
     * */
    @JvmStatic
    suspend fun resolve(entry: Entry, storage: Storage): Set<Link> {
        return storage.resolve(entry.link)
    }

    /**
     * Reconstruct the whole content according to the given [entry].
     * The blobs must be recovered first.
     * */
    @JvmStatic
    suspend fun restore(entry: Entry, storage: Storage, root: File) {
        if (root.exists()) {
            check(root.isDirectory) { "Root folder is a file: ${root.canonicalPath}" }
        } else {
            check(root.mkdirs()) { "Failed to create root folder: ${root.canonicalPath}" }
        }
        internalRestore(entry.link, storage, File(root, entry.name))
    }

    private suspend fun internalRestore(link: Link, storage: Storage, root: File) {
        logger.info { "Restoring ${root.canonicalPath}" }
        when (link.type) {
            Link.Type.BLOB -> {
                val blob = storage.read(link) as Blob
                withContext(Dispatchers.IO) {
                    Files.write(root.toPath(), blob.data, StandardOpenOption.CREATE)
                }
            }

            Link.Type.LIST -> {
                val links = LinkedList<Link>()
                (storage.read(link) as ListObject).also {
                    links.addAll(it.content)
                }

                withContext(Dispatchers.IO) {
                    Files.newOutputStream(root.toPath(), StandardOpenOption.CREATE)
                }.use {
                    while (links.isNotEmpty()) {
                        val l = links.pollFirst()
                        when (l.type) {
                            Link.Type.BLOB -> {
                                val blob = storage.read(l) as Blob
                                it.write(blob.data)
                            }

                            Link.Type.LIST -> {
                                val listObj = storage.read(l) as ListObject
                                links.addAll(0, listObj.content)
                            }

                            else -> error("Illegal type in list: ${l.type}")
                        }
                    }
                }
            }

            Link.Type.TREE -> {
                val treeObj = storage.read(link) as TreeObject
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
    fun listEntry(storage: Storage): Sequence<Entry> {
        return storage.listEntry()
    }

    /**
     * Delete entry
     * */
    @JvmStatic
    fun deleteEntry(entry: Entry, storage: Storage) = runBlocking {
        storage.removeEntry(entry.id)
    }


    /**
     * Delete all unreachable objects
     * */
    @JvmStatic
    fun gc(storage: Storage) = runBlocking {
        val listObjectsFuture = async { storage.listObjects() }
        val workingQueue = LinkedList<Link>()
        logger.info { "Listing entries..." }
        workingQueue.addAll(storage.listEntry().map { it.link })
        logger.info { "Listed ${workingQueue.size} entries" }
        val waitingQueue = LinkedList<Deferred<AritegObject>>()
        logger.info { "Listing current objects..." }
        // get all objects and remove reachable objects
        val (blobs, lists, trees) = listObjectsFuture.await()
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
                        waitingQueue.add(async { storage.read(link) })
                        lists.remove(link.hash)
                    } // otherwise, the link and its sub links are visited
                }

                Link.Type.TREE -> {
                    if (trees.contains(link.hash)) {
                        waitingQueue.add(async { storage.read(link) })
                        trees.remove(link.hash)
                    }
                }
            }

            if (workingQueue.isEmpty()) {
                // get result from waiting queue and put into working queue
                logger.info { "${blobs.size} blobs remain, ${lists.size} lists remain, ${trees.size} trees remain" }
                logger.info { "${waitingQueue.size} objects are fetching..." }
                waitingQueue.forEach {
                    when (val obj = it.await()) {
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
        val deletingQueue = LinkedList<Deferred<Unit>>()
        blobs.forEach { deletingQueue.add(async { storage.delete(Link.blobRef(it)) }) }
        lists.forEach { deletingQueue.add(async { storage.delete(Link.listRef(it)) }) }
        trees.forEach { deletingQueue.add(async { storage.delete(Link.treeRef(it)) }) }
        deletingQueue.forEach { it.await() }
    }

    @JvmStatic
    fun integrityCheck(storage: Storage, deleting: Boolean = false) = runBlocking {
        val listObjectsFuture = async { storage.listObjects() }
        logger.info { "Listing current objects..." }
        // get all objects and remove reachable objects
        val blobs = listObjectsFuture.await().first
        logger.info { "Listed ${blobs.size} blobs" }

        val taskChannel = Channel<Pair<Int, Link>>(Channel.UNLIMITED)
        val brokenListChannel = Channel<Link>(Channel.UNLIMITED)

        repeat(Runtime.getRuntime().availableProcessors() * 2) {
            launch {
                for (task in taskChannel) {
                    try {
                        storage.read(task.second)
                    } catch (e: HashNotMatchException) {
                        // exception means the hash is not correct
                        logger.info { "Hash not match on ${task.second.hash}" }
                        brokenListChannel.send(task.second)
                    }
                    if (task.first % 1000 == 0) {
                        logger.info { "Checked ${task.first / 1000}K blobs" }
                    }
                }
                brokenListChannel.close()
            }
        }

        val brokenListFuture = async { brokenListChannel.toList() }

        blobs.forEachIndexed { index, hash ->
            val link = Link.blobRef(hash)
            taskChannel.send(index to link)
        }
        taskChannel.close()
        val brokenList = brokenListFuture.await()

        logger.info { "Found ${brokenList.size} broken blobs:" }
        brokenList.forEach {
            logger.info { "(${it.type})${it.hash}" }
        }
        if (deleting) {
            logger.info { "Deleting broken blobs..." }
            brokenList
                .map { async { storage.delete(it) } }
                .forEach { it.await() }
        }
    }
}

