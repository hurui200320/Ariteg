package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.AritegObject
import info.skyblond.ariteg.storage.obj.Entry
import info.skyblond.ariteg.storage.obj.Link
import kotlin.jvm.Throws

interface Storage : AutoCloseable {
    /**
     * Write an object into the system, async, return the link of the object.
     * */
    @Throws(ObjectAlreadyExistsException::class)
    suspend fun write(type: Link.Type, obj: AritegObject): Link

    /**
     * Read a link from system, async, return the object.
     * */
    @Throws(ObjectNotFoundException::class)
    suspend fun read(link: Link): AritegObject

    /**
     * Delete a link from system, remove the object from storage.
     * Async, return nothing.
     * */
    suspend fun delete(link: Link)

    /**
     * Returns all link of a given type.
     * Including unreachable and broken ones.
     * */
    fun listObjects(type: Link.Type): Sequence<Link>

    /**
     * List all related links that can be reached from the [rootLink].
     * Async, return the sequence of the link.
     * */
    fun resolve(rootLink: Link): Sequence<Link>

    /**
     * Write an entry into the system. Return the entry. Async.
     * The entry might have different name when the name has been use.
     * The returned one contains the actual name.
     * */
    suspend fun addEntry(entry: Entry): Entry

    /**
     * Remove the entry by id. Async, return nothing.
     * */
    suspend fun removeEntry(entryName: String)

    /**
     * Get the entry by id. Async.
     * */
    suspend fun getEntry(entryName: String): Entry

    /**
     * List all entry in the system. Request new data on the go.
     * */
    fun listEntry(): Sequence<Entry>
}
