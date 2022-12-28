package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import info.skyblond.ariteg.storage.obj.*
import kotlinx.coroutines.*
import java.util.*

abstract class AbstractStorage<PathType : Any> : Storage {

    // -------------------- Encryption related --------------------
    protected abstract val key: ByteArray?

    private fun prepareDataBeforeWrite(data: ByteArray): ByteArray = key?.let { Cipher.encrypt(it, data) } ?: data

    private fun prepareDataAfterRead(data: ByteArray): ByteArray = key?.let { Cipher.decrypt(it, data) } ?: data

    // -------------------- END --------------------

    // -------------------- Read and Write obj --------------------

    /**
     * Map the link (represented by the [type] and [name]) to a path.
     * The path can be [java.io.File], [String], or something else.
     * */
    protected abstract fun mapToPath(type: String, name: String): PathType
    private fun mapToPath(type: Link.Type, name: String): PathType =
        mapToPath(type.name, name)

    /**
     * Write the given data into storage.
     * If the object exists(the path has been used),
     * throw [ObjectAlreadyExistsException]
     * */
    @Throws(ObjectAlreadyExistsException::class)
    protected abstract suspend fun internalWrite(path: PathType, data: ByteArray)

    /**
     * Read raw data from storage and return.
     * */
    @Throws(ObjectNotFoundException::class)
    protected abstract suspend fun internalRead(path: PathType): ByteArray

    /**
     * Delete the link quietly
     * */
    protected abstract suspend fun internalDelete(path: PathType)

    @Throws(ObjectAlreadyExistsException::class)
    final override suspend fun write(type: Link.Type, obj: AritegObject): Link {
        val path = mapToPath(type, obj.hashString)
        val data = prepareDataBeforeWrite(obj.encoded)

        PathMutex.usePath(path) {
            try {
                internalWrite(path, data)
            } catch (e: ObjectAlreadyExistsException) {
                // proto exists, read and check content
                val content = prepareDataAfterRead(internalRead(path))
                check(obj.encoded.contentEquals(content)) {
                    "Hash collision detected on ${obj.hashString} (Or wrong password)"
                }
            }
        }

        return Link(obj.hashString, type, obj.encoded.size)
    }

    @Throws(ObjectNotFoundException::class)
    final override suspend fun read(link: Link): AritegObject {
        val path = mapToPath(link.type, link.hash)
        val data = prepareDataAfterRead(internalRead(path))
        return when (link.type) {
            Link.Type.BLOB -> Blob(data)
            Link.Type.LIST -> ListObject(data)
            Link.Type.TREE -> TreeObject(data)
        }.also { it.verify(link.hash) }
    }

    final override suspend fun delete(link: Link) {
        internalDelete(mapToPath(link.type, link.hash))
    }

    final override fun resolve(rootLink: Link): Sequence<Link> = sequence {
        val queue = LinkedList(listOf(rootLink))
        while (queue.isNotEmpty()) {
            val link = queue.pop()
            if (link.type != Link.Type.BLOB) {
                when (val obj = runBlocking { read(link) }) {
                    is ListObject -> queue.addAll(0, obj.content)
                    is TreeObject -> queue.addAll(0, obj.content.map { it.value })
                }
            }
            yield(link)
        }
    }

    final override suspend fun addEntry(entry: Entry): Entry = coroutineScope {
        var resultEntry = entry

        while (true) {
            val path = mapToPath("entry", resultEntry.name)
            val data = resultEntry.encoded
            try {
                withContext(Dispatchers.IO) { internalWrite(path, data) }
                break
            } catch (e: ObjectAlreadyExistsException) {
                resultEntry = resultEntry.withName("dup_" + resultEntry.name)
            }
        }

        resultEntry
    }

    final override suspend fun getEntry(entryName: String): Entry {
        val path = mapToPath("entry", entryName)
        // do not encrypt entry
        return Entry(internalRead(path))
    }

    final override suspend fun removeEntry(entryName: String) {
        val path = mapToPath("entry", entryName)
        internalDelete(path)
    }

    // -------------------- END --------------------

    // -------------------- Listing things --------------------

    /**
     * Get all entry's [PathType].
     * */
    protected abstract fun listEntryPath(): Sequence<PathType>

    final override fun listEntry(): Sequence<Entry> =
        listEntryPath().map {
            Entry(runBlocking { internalRead(it) })
        }
}
