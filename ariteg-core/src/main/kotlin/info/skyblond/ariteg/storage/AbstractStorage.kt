package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import kotlinx.coroutines.*
import java.util.*

abstract class AbstractStorage<PathType : Any> : Storage {

    protected abstract val key: ByteArray?

    override fun isEncrypted(): Boolean = key != null

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


    final override suspend fun write(type: Link.Type, obj: AritegObject): Link {
        val hash = obj.getHashString()
        val path = mapToPath(type.name, hash)
        val rawData = obj.encodeToBytes()

        PathMutex.usePath(path) {
            try {
                val data = getData(rawData)
                withContext(Dispatchers.IO) {
                    internalWrite(path, data)
                }
            } catch (e: ObjectAlreadyExistsException) {
                // proto exists, read and check content
                val content = parseData(withContext(Dispatchers.IO) {
                    internalRead(path)
                })
                check(rawData.contentEquals(content)) {
                    "Hash collision detected on $hash (Or wrong password)"
                }
            }
        }

        return Link(hash, type, rawData.size.toLong())
    }


    final override suspend fun read(link: Link): AritegObject {
        val path = mapToPath(link.type.name, link.hash)
        val data = parseData(withContext(Dispatchers.IO) {
            internalRead(path)
        })
        return when (link.type) {
            Link.Type.BLOB -> {
                Blob(data).also { it.verify(link.hash) }
            }

            Link.Type.LIST -> {
                val json = data.decodeToString()
                ListObject.fromJson(json).also { it.verify(link.hash) }
            }

            Link.Type.TREE -> {
                val json = data.decodeToString()
                TreeObject.fromJson(json).also { it.verify(link.hash) }
            }
        }
    }

    final override suspend fun delete(link: Link) {
        val path = mapToPath(link.type.name, link.hash)
        withContext(Dispatchers.IO) {
            internalDelete(path)
        }
    }

    final override suspend fun resolve(rootLink: Link): Set<Link> = coroutineScope {
        val queue = LinkedList(listOf(rootLink))
        val result = HashSet<Link>()
        val futureList = LinkedList<Deferred<AritegObject>>()

        while (queue.isNotEmpty()) {
            val link = queue.pop()
            when (link.type) {
                Link.Type.BLOB -> result.add(link)
                else -> futureList.add(async { read(link) })
            }
            if (queue.isEmpty()) {
                futureList.forEach {
                    when (val obj = it.await()) {
                        is ListObject -> queue.addAll(obj.content)
                        is TreeObject -> queue.addAll(obj.content)
                    }
                }
                futureList.clear()
            }
        }
        result
    }


    final override suspend fun listObjects(): Triple<Set<String>, Set<String>, Set<String>> =
        withContext(Dispatchers.IO) {
            val blobSet = listHashInPath(getParentPath(mapToPath(Link.Type.BLOB.name, "something")))
            val listSet = listHashInPath(getParentPath(mapToPath(Link.Type.LIST.name, "something")))
            val treeSet = listHashInPath(getParentPath(mapToPath(Link.Type.TREE.name, "something")))
            Triple(blobSet, listSet, treeSet)
        }

    final override suspend fun addEntry(entry: Entry): Entry = withContext(Dispatchers.IO) {
        val path = mapToPath("entry", entry.id)
        val data = entry.toJson().encodeToByteArray()
        withContext(Dispatchers.IO) { internalWrite(path, data) }
        entry
    }

    final override suspend fun getEntry(entryId: String): Entry = withContext(Dispatchers.IO) {
        val path = mapToPath("entry", entryId)
        val json = withContext(Dispatchers.IO) { internalRead(path) }.decodeToString()
        Entry.fromJson(json)
    }

    final override suspend fun removeEntry(entryId: String) = withContext(Dispatchers.IO) {
        val path = mapToPath("entry", entryId)
        internalDelete(path)
    }

    final override fun listEntry(): Sequence<Entry> {
        val parentPath = getParentPath(mapToPath("entry", "something"))
        return sequence {
            listPathInPath(parentPath).forEach {
                val json = internalRead(it).decodeToString()
                yield(Entry.fromJson(json))
            }
        }
    }

    override fun close() {
        // nop
    }
}
