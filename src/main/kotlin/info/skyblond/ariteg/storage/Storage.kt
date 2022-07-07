package info.skyblond.ariteg.storage

import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link
import java.util.concurrent.CompletableFuture

interface Storage : AutoCloseable {
    fun write(type: Link.Type, obj: AritegObject): CompletableFuture<Link>

    fun read(link: Link): CompletableFuture<out AritegObject>

    fun delete(link: Link): CompletableFuture<Void>

    /**
     * List all objects, return in "blobs, lists, trees" style, hashes.
     * */
    fun listObjects(): CompletableFuture<Triple<Set<String>, Set<String>, Set<String>>>

    fun resolve(rootLink: Link): CompletableFuture<Set<Link>>

    fun addEntry(entry: Entry): CompletableFuture<Entry>

    fun removeEntry(entry: Entry): CompletableFuture<Void>

    fun listEntry(): Iterable<Entry>
}
