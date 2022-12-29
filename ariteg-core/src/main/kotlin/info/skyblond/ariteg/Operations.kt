package info.skyblond.ariteg

import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.storage.HashNotMatchException
import info.skyblond.ariteg.storage.Storage
import info.skyblond.ariteg.storage.obj.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

object Operations {
    private val logger = KotlinLogging.logger("Operations")
    private const val MEGA_BYTE = 1024 * 1024
    private fun format(number: Double): String {
        return "%.2f".format(number)
    }

    /**
     * Digest the [root] file, slice it and save as an [Entry]
     * */
    @JvmStatic
    suspend fun digest(root: File, slicer: Slicer, storage: Storage): Entry {
        val rootLink = internalDigest(root, slicer, storage)
        val entry = Entry(root.name, rootLink.await(), ZonedDateTime.now())
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
                // use semaphore to limit the memory usage
                val s = Semaphore(Runtime.getRuntime().availableProcessors() * 2)

                withContext(Dispatchers.IO) {
                    Files.newInputStream(file.toPath(), StandardOpenOption.READ)
                }.use { fis ->
                    slicer.slice(fis).forEach { nextBlob ->
                        processedSize += nextBlob.data.size
                        futureLinks.add(async {
                            s.acquire()
                            storage.write(Link.Type.BLOB, nextBlob)
                                .also { s.release() }
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
                futureLinks[0].await()
            }

            file.isDirectory -> async {
                val files = file.listFiles() ?: error("List folder returns null")
                val subLinks = files.associate { f ->
                    // sync op here, the internal digest of file is already async
                    f.name to internalDigest(f, slicer, storage).await()
                }
                storage.write(Link.Type.TREE, TreeObject(subLinks))
            }

            else -> error("Bad file: ${file.canonicalPath}")
        }
    }


    /**
     * Get all [Blob] links related to the given [entry].
     * By design, non-blob objects should not be archived and can be accessed instantly.
     * */
    @JvmStatic
    fun resolve(entry: Entry, storage: Storage): Sequence<Link> {
        return storage.resolve(entry.root)
    }

    /**
     * Reconstruct the whole content according to the given [entry].
     * The blobs must be recovered first.
     * */
    @JvmStatic
    suspend fun restore(entry: Entry, storage: Storage, root: File) {
        if (root.exists()) {
            check(root.isDirectory) { "Root folder is not a folder: ${root.canonicalPath}" }
        } else {
            check(root.mkdirs()) { "Failed to create root folder: ${root.canonicalPath}" }
        }
        internalRestore(entry.root, storage, File(root, entry.name))
    }

    private suspend fun internalRestore(link: Link, storage: Storage, root: File): Unit = coroutineScope {
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
                                withContext(Dispatchers.IO) { it.write(blob.data) }
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
                // parallel restore for tree
                treeObj.content.map { e ->
                    async { internalRestore(e.value, storage, File(root, e.key)) }
                }.forEach { it.await() }
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
        storage.removeEntry(entry.name)
    }


    /**
     * Delete all unreachable objects
     * */
    @JvmStatic
    fun gc(storage: Storage) = runBlocking {
        val blobsFuture = async { storage.listObjects(Link.Type.BLOB).toMutableSet() }
        val listsFuture = async { storage.listObjects(Link.Type.LIST).toMutableSet() }
        val treesFuture = async { storage.listObjects(Link.Type.TREE).toMutableSet() }
        val workingQueue = LinkedList<Link>()
        logger.info { "Listing entries..." }
        workingQueue.addAll(storage.listEntry().map { it.root })
        logger.info { "Listed ${workingQueue.size} entries" }
        val waitingQueue = LinkedList<Deferred<AritegObject>>()
        logger.info { "Listing current objects..." }
        // get all objects and remove reachable objects
        val blobs = blobsFuture.await()
        val lists = listsFuture.await()
        val trees = treesFuture.await()
        logger.info { "Listed ${blobs.size} blobs, ${lists.size} lists, and ${trees.size} trees" }
        while (workingQueue.isNotEmpty()) {
            val link = workingQueue.poll()!!
            when (link.type) {
                Link.Type.BLOB -> {
                    blobs.remove(link)
                }

                Link.Type.LIST -> {
                    if (lists.contains(link)) {
                        // the sub links are not visited yet
                        // submit the job and waiting
                        waitingQueue.add(async { storage.read(link) })
                        lists.remove(link)
                    } // otherwise, the link and its sub links are visited
                }

                Link.Type.TREE -> {
                    if (trees.contains(link)) {
                        waitingQueue.add(async { storage.read(link) })
                        trees.remove(link)
                    }
                }
            }

            if (workingQueue.isEmpty()) {
                // get result from waiting queue and put into working queue
                logger.info { "${blobs.size} blobs remain, ${lists.size} lists remain, ${trees.size} trees remain" }
                logger.info { "${waitingQueue.size} objects are fetching..." }
                waitingQueue.forEach { deferred ->
                    when (val obj = deferred.await()) {
                        is ListObject -> workingQueue.addAll(obj.content)
                        is TreeObject -> workingQueue.addAll(obj.content.map { it.value })
                    }
                }
                waitingQueue.clear()
                logger.info { "Checking ${workingQueue.size} objects..." }
            }
        }
        logger.info { "Found ${blobs.size} unreachable blobs, ${lists.size} unreachable lists, and ${trees.size} unreachable trees" }
        // now what we left are unreachable objects
        val deletingQueue = LinkedList<Deferred<Unit>>()
        blobs.forEach { deletingQueue.add(async { storage.delete(it) }) }
        lists.forEach { deletingQueue.add(async { storage.delete(it) }) }
        trees.forEach { deletingQueue.add(async { storage.delete(it) }) }
        deletingQueue.forEach { it.await() }
    }

    @JvmStatic
    fun integrityCheck(storage: Storage, deleting: Boolean = false) = runBlocking {
        logger.info { "Listing current objects..." }
        // get all objects and remove reachable objects
        val blobs = storage.listObjects(Link.Type.BLOB)
        logger.info { "Listed ${blobs.count()} blobs. Start checking..." }
        val brokenListChannel = Channel<Link>(Channel.UNLIMITED)
        launch {
            var counter = 0L
            for (link in brokenListChannel) {
                counter += 1
                logger.warn { "Broken ${link.type}: ${link.hash}" }
                if (deleting) storage.delete(link)
            }
            logger.info { "Found $counter broken blobs." }
        }
        val taskChannel = Channel<Link>(Channel.UNLIMITED)
        val taskCounter = AtomicLong(0)

        repeat(Runtime.getRuntime().availableProcessors() * 2) {
            launch {
                for (link in taskChannel) {
                    try {
                        storage.read(link)
                    } catch (e: HashNotMatchException) {
                        // exception means the hash is not correct
                        brokenListChannel.send(link)
                    }
                    taskCounter.decrementAndGet()
                }
            }
        }
        blobs.forEach { taskCounter.incrementAndGet(); taskChannel.send(it) }
        taskChannel.close()
        while (taskCounter.get() != 0L) {
            delay(1000)
        }
        brokenListChannel.close()
    }
}

