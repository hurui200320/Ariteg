package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import java.util.*
import java.util.concurrent.CompletableFuture

abstract class AbstractStorage<PathType> : Storage {

    protected abstract val key: ByteArray?

    override fun isEncrypted(): Boolean = key != null

    /**
     * In case multiple thread write the same file at the same time,
     * this set records all writing paths. When writing, first claim
     * in this set, after write, remove the entry from this set.
     * Check this set before writing, wait until the entry is removed.
     * */
    private val writingPathSet = HashSet<PathType>()

    /**
     * Map the link (represented by the [type] and [name]) to a path.
     * The path can be [java.io.File], [String], or something else.
     * */
    protected abstract fun mapToPath(type: String, name: String): PathType

    /**
     * Get parent path from the given [path].
     * */
    protected abstract fun getParentPath(path: PathType): PathType

    protected class ObjectAlreadyExistsException : RuntimeException()

    /**
     * Write the given data into storage.
     * If the object exists(the path has been used),
     * throw [ObjectAlreadyExistsException]
     * */
    protected abstract fun internalWrite(path: PathType, data: ByteArray)

    /**
     * Read raw data from storage and return.
     * */
    protected abstract fun internalRead(path: PathType): ByteArray

    /**
     * Delete the link quietly
     * */
    protected abstract fun internalDelete(path: PathType)

    /**
     * Get all object's path who is setting in the [parentPath].
     * Aka, list all sub files in a father file; or list all sub keys
     * in a prefix.
     * */
    protected abstract fun listPathInPath(parentPath: PathType): List<PathType>

    /**
     * Unlike [listPathInPath], [listHashInPath] do not list path,
     * but object hash (or entry id).
     * */
    protected abstract fun listHashInPath(parentPath: PathType): Set<String>


    private fun getData(data: ByteArray): ByteArray = if (key != null) encrypt(key!!, data) else data

    private fun parseData(data: ByteArray): ByteArray = if (key != null) decrypt(key!!, data) else data


    final override fun write(type: Link.Type, obj: AritegObject): CompletableFuture<Link> =
        CompletableFuture.supplyAsync {
            val hash = obj.getHashString().get()
            val path = mapToPath(type.name, hash)
            val rawData = obj.encodeToBytes()

            // make sure only one thread write into one file
            while (true) {
                var exitFlag = false
                synchronized(writingPathSet) {
                    // no on is writing, then we write
                    if (!writingPathSet.contains(path)) {
                        writingPathSet.add(path)
                        exitFlag = true
                    }
                    // otherwise, return the lock and waiting
                }
                if (exitFlag) {
                    break
                } else {
                    try {
                        Thread.sleep(1000)
                    } catch (_: Throwable) {
                    }
                }
            }

            try {
                internalWrite(path, getData(rawData))
            } catch (e: ObjectAlreadyExistsException) {
                // proto exists, read and check content
                val content = parseData(internalRead(path))
                check(rawData.contentEquals(content)) {
                    "Hash collision detected on $hash (Or wrong password)"
                }
            }

            synchronized(writingPathSet) {
                writingPathSet.remove(path)
            }

            Link(hash, type, rawData.size.toLong())
        }


    final override fun read(link: Link): CompletableFuture<out AritegObject> =
        CompletableFuture.supplyAsync {
            val path = mapToPath(link.type.name, link.hash)
            when (link.type) {
                Link.Type.BLOB -> {
                    val data = parseData(internalRead(path))
                    Blob(data).also { it.verify(link.hash) }
                }

                Link.Type.LIST -> {
                    val json = parseData(internalRead(path)).decodeToString()
                    ListObject.fromJson(json).also { it.verify(link.hash) }

                }

                Link.Type.TREE -> {
                    val json = parseData(internalRead(path)).decodeToString()
                    TreeObject.fromJson(json).also { it.verify(link.hash) }
                }
            }
        }

    final override fun delete(link: Link): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val path = mapToPath(link.type.name, link.hash)
            internalDelete(path)
        }

    final override fun resolve(rootLink: Link): CompletableFuture<Set<Link>> = CompletableFuture.supplyAsync {
        val queue = LinkedList(listOf(rootLink))
        val result = HashSet<Link>()
        val futureList = LinkedList<CompletableFuture<out AritegObject>>()

        while (queue.isNotEmpty()) {
            val link = queue.pop()
            when (link.type) {
                Link.Type.BLOB -> result.add(link)
                else -> futureList.add(read(link))
            }
            if (queue.isEmpty()) {
                futureList.forEach {
                    when (val obj = it.get()) {
                        is ListObject -> {
                            queue.addAll(obj.content)
                        }

                        is TreeObject -> {
                            queue.addAll(obj.content)
                        }
                    }
                }
                futureList.clear()
            }
        }

        result
    }


    final override fun listObjects(): CompletableFuture<Triple<Set<String>, Set<String>, Set<String>>> =
        CompletableFuture.supplyAsync {
            val blobSet = listHashInPath(getParentPath(mapToPath(Link.Type.BLOB.name, "something")))
            val listSet = listHashInPath(getParentPath(mapToPath(Link.Type.LIST.name, "something")))
            val treeSet = listHashInPath(getParentPath(mapToPath(Link.Type.TREE.name, "something")))
            Triple(blobSet, listSet, treeSet)
        }

    final override fun addEntry(entry: Entry): CompletableFuture<Entry> = CompletableFuture.supplyAsync {
        val path = mapToPath("entry", entry.id)
        val data = entry.toJson().encodeToByteArray()
        internalWrite(path, data)
        entry
    }

    final override fun getEntry(entryId: String): CompletableFuture<Entry> = CompletableFuture.supplyAsync {
        val path = mapToPath("entry", entryId)
        val json = internalRead(path).decodeToString()
        Entry.fromJson(json)
    }

    final override fun removeEntry(entryId: String): CompletableFuture<Void> = CompletableFuture.runAsync {
        val path = mapToPath("entry", entryId)
        internalDelete(path)
    }

    final override fun listEntry(): Iterable<Entry> {
        val parentPath = getParentPath(mapToPath("entry", "something"))
        val listOfPath = listPathInPath(parentPath)
        return object : Iterable<Entry> {
            override fun iterator(): Iterator<Entry> {
                return object : Iterator<Entry> {
                    private var pointer = 0

                    override fun hasNext(): Boolean = pointer < listOfPath.size

                    override fun next(): Entry {
                        if (!hasNext()) throw NoSuchElementException("No next elements")
                        val json = internalRead(listOfPath[pointer++]).decodeToString()
                        return Entry.fromJson(json)
                    }
                }
            }
        }
    }

    override fun close() {
        // nop
    }
}
