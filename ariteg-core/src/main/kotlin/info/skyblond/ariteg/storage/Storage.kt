package info.skyblond.ariteg.storage

import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link

interface Storage : AutoCloseable {
    /**
     * Return true if the storage is encrypted.
     * */
    fun isEncrypted(): Boolean

    /**
     * Write an object into the system, async, return the link of the object.
     * */
    suspend fun write(type: Link.Type, obj: AritegObject): Link

    /**
     * Read a link from system, async, return the object.
     * */
    suspend fun read(link: Link): AritegObject

    /**
     * Delete a link from system, remove the object from storage.
     * Async, return nothing.
     * */
    suspend fun delete(link: Link)

    /**
     * List all objects, return in "blobs, lists, trees" style, hashes.
     * */
    suspend fun listObjects(): Triple<Set<String>, Set<String>, Set<String>>

    /**
     * List all blobs that can be reached from the [rootLink].
     * Async, return the set of the link.
     * */
    suspend fun resolve(rootLink: Link): Set<Link>

    /**
     * Write a entry into the system. Return the entry. Async.
     * */
    suspend fun addEntry(entry: Entry): Entry

    /**
     * Remove the entry by id. Async, return nothing.
     * */
    suspend fun removeEntry(entryId: String)

    /**
     * Get the entry by id. Async.
     * */
    suspend fun getEntry(entryId: String): Entry

    /**
     * List all entry in the system. Request new data on the go.
     * */
    fun listEntry(): Sequence<Entry>
}
