package info.skyblond.ariteg.cmd.fuse

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import info.skyblond.ariteg.cmd.CmdContext
import info.skyblond.ariteg.storage.obj.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.FileNotFoundException
import java.math.BigInteger
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * This object holds the caches for random access needs.
 * */
object RandomAccessCache {
    private val logger = KotlinLogging.logger {  }
    // ------------------------------ BLOB cache ------------------------------
    /**
     * The blobs are immutable, but caching them cost a lot of RAM.
     * This cache is design to ease the burst read against the same blob.
     * Without cache, each read operation needs a dedicate request to backend,
     * resulting in high latency and low performance.
     * By caching the blob in RAM for a short time, it will make unaligned read
     * fast (not aligned to blobs).
     * */
    private val blobContentCache: Cache<String, ByteArray> = Caffeine.newBuilder()
        .softValues()
        .build()

    fun getCachedBlob(link: Link): Blob? {
        if (link.type != Link.Type.BLOB) return null
        // return if found
        blobContentCache.getIfPresent(link.hash)?.let { return Blob(it) }
        // no luck, read it
        // here might be multiple thread reading one blob, introducing lock will make
        // things complicated. Thus, we (I) can tolerate that.
        return try {
            val blob = runBlocking { CmdContext.storage.read(link) as Blob }
            blobContentCache.put(link.hash, blob.data)
            blob
        } catch (t: Throwable) {
            logger.error(t) { "Failed to read $link" }
            null
        }
    }

    // ------------------------------ ListAndTree cache ------------------------------
    /**
     * List and Tree objects are also immutable, and relatively small.
     * Thus, they can be massively cached in memory and save a lot of indexing time.
     * There are no size or duration limit for them,
     * as long as there are space for them in RAM.
     * */
    private val listCache: Cache<String, ListObject> = Caffeine.newBuilder()
        .softValues()
        .build()

    private fun getCachedList(link: Link): ListObject? {
        if (link.type != Link.Type.LIST) return null
        // return if found
        listCache.getIfPresent(link.hash)?.let { return it }
        // not found, read it
        return try {
            val list = runBlocking { CmdContext.storage.read(link) as ListObject }
            listCache.put(link.hash, list)
            list
        } catch (t: Throwable) {
            logger.error(t) { "Failed to read $link" }
            null
        }
    }

    private val treeCache: Cache<String, TreeObject> = Caffeine.newBuilder()
        .softValues()
        .build()

    fun getCachedTree(link: Link): TreeObject? {
        if (link.type != Link.Type.TREE) return null
        // return if found
        treeCache.getIfPresent(link.hash)?.let { return it }
        // not found, read it
        return try {
            val tree = runBlocking { CmdContext.storage.read(link) as TreeObject }
            treeCache.put(link.hash, tree)
            tree
        } catch (t: Throwable) {
            logger.error(t) { "Failed to read $link" }
            null
        }
    }

    // ------------------------------ Entry cache ------------------------------
    /**
     * Cache entry, so it will be faster when resolving path.
     * The path can be updated, so each cache's TTL is 10s.
     * */
    private val entryCache: Cache<String, Entry> = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .softValues()
        .build()

    fun getCachedEntry(name: String): Entry? {
        // return if found
        entryCache.getIfPresent(name)?.let { return it }
        // not found, read it
        return try {
            val entry = runBlocking { CmdContext.storage.getEntry(name) }
            entryCache.put(name, entry)
            entry
        } catch (t: ExecutionException) {
            if (t.cause !is FileNotFoundException)
                logger.error(t) { "Failed to read entry $name" }
            null
        } catch (t: Throwable) {
            logger.error(t) { "Failed to read entry $name" }
            null
        }
    }

    // ------------------------------ Blob index cache ------------------------------
    data class FileIndexEntry(
        val offset: Long,
        val link: Link
    )

    /**
     * This is the cache for random access index.
     * On disk, Files can be represented in a messy style:
     * List[BLOB, List[BLOB, BLOB], LIST[LIST[BLOB, BLOB], BLOB],...]
     * It can be a fancy tree, but not easy to make random access.
     * During reading, it would be great to have a flattened list:
     * List[BLOB, BLOB, BLOB, ...]
     * To make it even faster, the index will log each blob's offset:
     * [0K -> BLOB#1, 16K -> BLOB#15, 32K -> BLOB#34, ...]
     *
     * This cache is essential to read operations, so keep it longer in the RAM.
     *
     * About long: The offset from FUSE is Long. We don't need BigInteger.
     * */
    private val blobIndexCache: Cache<String, List<FileIndexEntry>> = Caffeine.newBuilder()
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .build()

    private fun flatten(link: Link): List<Link> {
        return when (link.type) {
            Link.Type.BLOB -> listOf(link)

            Link.Type.LIST -> getCachedList(link)!!.content.flatMap { flatten(it) }

            else -> emptyList()
        }
    }

    fun getIndex(link: Link): List<FileIndexEntry>? {
        // return if found
        blobIndexCache.getIfPresent(link.hash)?.let { return it }
        // not found
        return try {
            val flattened = flatten(link)
            var offset = 0L
            val result = flattened.map { l ->
                (FileIndexEntry(offset, l)).also { offset += l.size }
            }
            blobIndexCache.put(link.hash, result)
            result
        } catch (t: Throwable) {
            logger.error(t) { "Failed to build index for $link" }
            null
        }
    }

    // ------------------------------ some fancy utils ------------------------------

    /**
     * Show as total inodes count in `statfs`.
     * */
    fun getCachedObjectsCount(): Long {
        val blobs = blobContentCache.asMap().keys.size.toLong()
        val lists = listCache.asMap().keys.size.toLong()
        val trees = treeCache.asMap().keys.size.toLong()
        return blobs + lists + trees
    }

    /**
     * Calculated blobs' size for a given folder or file.
     * Count as 0 if the link is failed to read.
     * */
    fun calculateFileSize(link: Link): BigInteger {
        return when (link.type) {
            Link.Type.BLOB -> link.size.toBigInteger()

            Link.Type.LIST -> getCachedList(link)?.content
                // calculate sub elem
                ?.map { calculateFileSize(it) }
                ?.sumOf { it } ?: BigInteger.ZERO

            Link.Type.TREE -> getCachedTree(link)?.content
                ?.map { calculateFileSize(it.value) }
                ?.sumOf { it } ?: BigInteger.ZERO
        }
    }
}
