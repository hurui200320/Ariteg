package info.skyblond.ariteg.storage

import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link
import java.util.concurrent.CompletableFuture

interface Storage : AutoCloseable {
    /**
     * Return true if the storage is encrypted.
     * */
    fun isEncrypted(): Boolean

    /**
     * Write an object into the system, async, return the link of the object.
     * */
    fun write(type: Link.Type, obj: AritegObject): CompletableFuture<Link>

    /**
     * Read a link from system, async, return the object.
     * */
    fun read(link: Link): CompletableFuture<out AritegObject>

    /**
     * Delete a link from system, remove the object from storage.
     * Async, return nothing.
     * */
    fun delete(link: Link): CompletableFuture<Void>

    /**
     * List all objects, return in "blobs, lists, trees" style, hashes.
     * */
    fun listObjects(): CompletableFuture<Triple<Set<String>, Set<String>, Set<String>>>

    /**
     * List all blobs that can be reached from the [rootLink].
     * Async, return the set of the link.
     * */
    fun resolve(rootLink: Link): CompletableFuture<Set<Link>>

    /**
     * Write a entry into the system. Return the entry. Async.
     * */
    fun addEntry(entry: Entry): CompletableFuture<Entry>

    /**
     * Remove the entry by id. Async, return nothing.
     * */
    fun removeEntry(entryId: String): CompletableFuture<Void>

    /**
     * List all entry in the system. Request new data on the go.
     * */
    fun listEntry(): Iterable<Entry>
}
