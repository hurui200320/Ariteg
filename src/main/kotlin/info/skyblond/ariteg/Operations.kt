package info.skyblond.ariteg

import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.storage.Storage
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.min

object Operations {
    private val logger = KotlinLogging.logger("Operations")

    /**
     * Digest the [root] file, slice it and save as an [Entry]
     * */
    fun digest(root: File, slicerProvider: (File) -> Slicer, storage: Storage): Entry {
        val rootLink = internalDigest(root, slicerProvider, storage)
        val entry = Entry(root.name, rootLink.get(), Date(), "")
        return storage.addEntry(entry).get()
    }

    private fun internalDigest(
        file: File,
        slicerProvider: (File) -> Slicer,
        storage: Storage
    ): CompletableFuture<Link> =
        when {
            file.isFile -> CompletableFuture.supplyAsync {
                val slicer = slicerProvider.invoke(file).iterator()
                // use semaphore to limit memory usage
                // aka how many pending blobs in memory
                val semaphore = ConfigService.uploadSemaphore
                val futureLinks = LinkedList<CompletableFuture<Link>>()
                // slice and store blobs
                while (slicer.hasNext()) {
                    semaphore.acquire()
                    val nextBlob = slicer.next()
                    futureLinks.add(
                        storage.write(Link.Type.BLOB, nextBlob)
                            .thenApply { semaphore.release(); it }
                    )
                }

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

                // The last one should be the root
                futureLinks[0].get().copy(name = file.name)
            }
            file.isDirectory -> CompletableFuture.supplyAsync {
                val files = file.listFiles() ?: error("List folder returns null")
                val futureLinks = files.map { f ->
                    internalDigest(f, slicerProvider, storage).thenApply {
                        it.copy(name = f.name)
                    }
                }
                TreeObject(futureLinks.map { it.get() }).let {
                    storage.write(Link.Type.TREE, it).get().copy(name = file.name)
                }
            }
            else -> error("Bad file: ${file.canonicalPath}")
        }


    /**
     * Get all [Blob] links related to the given [entry].
     * By design, non-blob objects should not be archived and can be accessed instantly.
     * */
    fun resolve(entry: Entry, storage: Storage): Set<Link> {
        return storage.resolve(entry.link).get()
    }

    /**
     * Send the command which recover the blob links to accessible status.
     * */
    fun recover(links: Collection<Link>, storage: Storage) {
        storage.recover(links).get()
    }


    /**
     * Warm up the data by downloading/caching the data.
     * */
    fun preload(entry: Entry, storage: Storage) {
        val links = resolve(entry, storage)
        val semaphore = ConfigService.preloadSemaphore
        links.map { link ->
            semaphore.acquire()
            storage.read(link).thenApply { semaphore.release(); it }
        }.map { it.get() }
    }

    /**
     * Reconstruct the whole content according to the given [entry].
     * The blobs must be recovered first.
     * */
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
    fun listEntry(storage: Storage): List<Entry> {
        return storage.listEntry().toList()
    }

    /**
     * Delete entry
     * */
    fun deleteEntry(entry: Entry, storage: Storage) {
        storage.removeEntry(entry).get()
    }

    /**
     * Delete all unreachable objects
     * */
    fun gc(storage: Storage) {
        val listObjectsFuture = storage.listObjects()
        val workingQueue = LinkedList<Link>()
        workingQueue.addAll(storage.listEntry().map { it.link })
        val waitingQueue = LinkedList<CompletableFuture<out AritegObject>>()
        // get all objects and remove reachable objects
        val (blobs, lists, trees) = listObjectsFuture.get()
            .let { Triple(it.first.toMutableSet(), it.second.toMutableSet(), it.third.toMutableSet()) }
        while (workingQueue.isNotEmpty()) {
            val link = workingQueue.poll()!!
            when (link.type) {
                Link.Type.BLOB -> blobs.remove(link.hash)
                Link.Type.LIST -> {
                    if (lists.contains(link.hash)) {
                        // the sub links are not visited yet
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
                waitingQueue.forEach {
                    when (val obj = it.get()) {
                        is ListObject -> workingQueue.addAll(obj.content)
                        is TreeObject -> workingQueue.addAll(obj.content)
                    }
                }
                waitingQueue.clear()
            }
        }
        // now what we left are unreachable objects
        val deletingQueue = LinkedList<CompletableFuture<Void>>()
        blobs.forEach { deletingQueue.add(storage.delete(Link(it, Link.Type.BLOB))) }
        lists.forEach { deletingQueue.add(storage.delete(Link(it, Link.Type.LIST))) }
        trees.forEach { deletingQueue.add(storage.delete(Link(it, Link.Type.TREE))) }
        deletingQueue.forEach { it.get() }
    }
}
