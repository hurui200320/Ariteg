package info.skyblond.ariteg.cmd.fuse

import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link
import info.skyblond.ariteg.cmd.CmdContext
import info.skyblond.ariteg.cmd.asOtcInt
import jnr.ffi.Pointer
import jnr.posix.FileStat.*
import ru.serce.jnrfuse.ErrorCodes
import ru.serce.jnrfuse.FuseFillDir
import ru.serce.jnrfuse.FuseStubFS
import ru.serce.jnrfuse.struct.FileStat
import ru.serce.jnrfuse.struct.FuseFileInfo
import ru.serce.jnrfuse.struct.Statvfs
import java.math.BigInteger
import kotlin.math.min

class AritegFS(
    /**
     * Owner uid. Null means no owner, aka everyone is owner.
     * */
    private val uid: Long? = null,
    /**
     * Owner gid. Null means no owner, aka everyone is owner.
     * */
    private val gid: Long? = null,
    /**
     * Permission mask for directories (TREE nodes). Default is 0755
     * */
    private val dirMask: Int = "0755".asOtcInt(),
    /**
     * Permission mask for files (LIST and BLOB nodes). Default is 0644
     * */
    private val fileMask: Int = "0644".asOtcInt(),
) : FuseStubFS() {
    private val logger = CmdContext.logger
    private val fsId: Long = System.currentTimeMillis()

    /**
     * Resolve fuse path to Tree or List/Blob link.
     * `/<Entry ID> <Entry name escaped>/<sub folder>/<path>`
     * The entry name will replace all `\` and `/` to `_`
     *
     * @return Pair of Link and error code. Error code = 0 means ok.
     * */
    private fun resolvePath(path: String): Pair<Link?, Int> =
        path.dropWhile { it == '/' } // remove leading slash
            .split('/')
            .let { seg ->
                val entryId = seg[0].split(" ")[0]
                var link = RandomAccessCache.getCachedEntry(entryId)?.link
                    ?: return null to -ErrorCodes.ENOENT()

                for (i in 1 until seg.size) {
                    val currentFindingEntryName = seg[i]
                    if (link.type != Link.Type.TREE) {
                        // if not folder, can't search
                        return null to -ErrorCodes.ENOENT()
                    }
                    // update link with result
                    link = RandomAccessCache.getCachedTree(link)?.content
                        ?.find { it.name == currentFindingEntryName }
                        ?: return null to -ErrorCodes.ENOENT()
                }
                return link to 0
            }

    private fun resolveEntry(path: String): Entry? =
        path.dropWhile { it == '/' } // remove leading slash
            .split('/')
            .let { seg ->
                val entryId = seg[0].split(" ")[0]
                RandomAccessCache.getCachedEntry(entryId)
            }

    private fun mapEntryToFileName(entry: Entry): String {
        return entry.id + " " + entry.name
            .replace('\\', '_') // windows separator
            .replace('/', '_') // fuse separator
    }

    private fun nockOffWritePermission(permission: Int): Int =
        permission and S_IWUSR.inv() and S_IWGRP.inv() and S_IWOTH.inv()

    override fun getattr(path: String, stat: FileStat): Int {
        stat.st_uid.set(uid ?: context.uid.get())
        stat.st_gid.set(gid ?: context.uid.get())

        // no atime will be set, since objs are shared on disk.

        if (path == "/") {
            // root folder
            stat.st_mode.set(
                FileStat.S_IFDIR or nockOffWritePermission(dirMask)
            )
            // no link, no st_nlink
            // no birthtime, ctime, mtime for root folder
        } else {
            // everything else
            val (link, errorCode) = resolvePath(path)
            if (link == null) return errorCode
            if (link.type == Link.Type.TREE) {
                // is a folder
                stat.st_mode.set(
                    FileStat.S_IFDIR or nockOffWritePermission(dirMask)
                )
            } else {
                // is a file
                stat.st_mode.set(
                    FileStat.S_IFREG or nockOffWritePermission(fileMask)
                )
                stat.st_size.set(RandomAccessCache.calculateFileSize(link))
            }
            // set time as entry
            resolveEntry(path)?.let { entry ->
                val unixTime = entry.time.time / 1000
                stat.st_birthtime.tv_sec.set(unixTime)
                stat.st_mtim.tv_sec.set(unixTime)
                stat.st_ctim.tv_sec.set(unixTime)
                stat.st_atim.tv_sec.set(System.currentTimeMillis() / 1000)
            }
        }
        return 0 // return success
    }

    private fun fillDirFiller(path: String, buf: Pointer, filler: FuseFillDir, names: List<String>) {
        names.forEach {
            // check and warn illegal char
            // these will not crash the program, but will cause malfunction
            if (it.contains('\\') || it.contains('/')) {
                logger.warn {
                    "Illegal char detected in filename: `$it`, at path `$path`. " +
                            "These will not crash the FUSE, but will cause malfunction, " +
                            "like missing file, wrong file, etc."
                }
            }
            filler.apply(buf, it, null, 0)
        }
    }

    /**
     * The readdir implementation ignores the offset parameter,
     * and passes zero to the filler function's offset.
     * The filler function will not return '1' (unless an error happens),
     * so the whole directory is read in a single readdir operation.
     * This works just like the old getdir() method.
     * */
    override fun readdir(
        path: String, buf: Pointer, filler: FuseFillDir, offset: Long, fi: FuseFileInfo?
    ): Int {
        // fixed two entries
        filler.apply(buf, ".", null, 0)
        filler.apply(buf, "..", null, 0)
        if (path == "/") {
            // root folder
            fillDirFiller(
                path, buf, filler,
                CmdContext.storage.listEntry().map { mapEntryToFileName(it) }
            )
        } else {
            // everything else
            val (link, errorCode) = resolvePath(path)
            if (link == null) return errorCode
            // the returned link should be a tree
            if (link.type != Link.Type.TREE) return -ErrorCodes.ENOENT()
            // fetch content and list
            try {
                val tree = RandomAccessCache.getCachedTree(link)
                // TreeObject ensure content has unique name
                fillDirFiller(path, buf, filler,
                    tree?.content?.map { it.name!! } ?: emptyList())
            } catch (t: Throwable) {
                logger.error(t) { "Failed to read object when reading dir" }
                return -ErrorCodes.EIO()
            }
        }
        return 0 // return success
    }

    override fun open(path: String, fi: FuseFileInfo?): Int {
        // request file index, warm up the cache
        val (link, errorCode) = resolvePath(path)
        if (link == null) return errorCode
        // the link should be list or blob.
        if (link.type == Link.Type.TREE) return -ErrorCodes.EISDIR()
        // build the index in advance
        RandomAccessCache.getIndex(link)
        return 0 // success
    }

    override fun read(
        path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo?
    ): Int {
        // everything else
        val (link, errorCode) = resolvePath(path)
        if (link == null) return errorCode
        // the link should be list or blob.
        if (link.type == Link.Type.TREE) return -ErrorCodes.EISDIR()
        // try to get index, and find the first blob we need
        val blobs = (RandomAccessCache.getIndex(link) ?: return -ErrorCodes.EIO())
            // starting offset + size of blob <= offset, skip it
            .dropWhile { (blobOffset, blobLink) -> blobOffset + blobLink.size < offset }
            // take only if starting offset is in range
            .takeWhile { (blobOffset, _) -> blobOffset <= offset + size }

        // read in a loop
        var readCount = 0L
        for (i in blobs.indices) {
            val objLink = blobs[i].link
            // if this is the first blob, we need to account the skipped part
            // else, reading from 0
            val iOffset = if (i != 0) 0 else offset - blobs.first().offset
            // find out how much data we read
            val iSize = min(objLink.size - iOffset, size - readCount)
            try {
                // read the blob
                val blob = RandomAccessCache.getCachedBlob(objLink) ?: return -ErrorCodes.EIO()
                // copy data
                buf.put(readCount, blob.data, iOffset.toInt(), iSize.toInt())
                readCount += iSize
            } catch (t: Throwable) {
                logger.error(t) { "Error when reading $link at path $path" }
                return -ErrorCodes.EIO()
            }
            if (readCount >= size)
                break
        }

        return readCount.toInt() // return the number of actual bytes read
    }

    private val blockUnit = 4 * 1024 * 1024

    override fun statfs(path: String, stbuf: Statvfs): Int {
        stbuf.f_bsize.set(blockUnit) // 4MB per block
        stbuf.f_frsize.set(blockUnit) // 4MB per unit
        stbuf.f_bfree.set(0) // no free space
        stbuf.f_bavail.set(0) // no free space
        // computational heavy op
        if (path == "/") {
            // get total file size
            val (result, remainder) = CmdContext.storage.listEntry()
                .map { RandomAccessCache.calculateFileSize(it.link) }
                .sumOf { it }.divideAndRemainder(blockUnit.toBigInteger())
            stbuf.f_blocks.set(
                if (remainder > BigInteger.ZERO) result + BigInteger.ONE else result
            )
        }
        stbuf.f_files.set(RandomAccessCache.getCachedObjectsCount())
        stbuf.f_ffree.set(0) // no free space
        stbuf.f_favail.set(0) // no free space
        stbuf.f_fsid.set(fsId) // use mounting time as id
        stbuf.f_flag.set((Statvfs.ST_RDONLY or Statvfs.ST_IMMUTABLE).toLong())
        stbuf.f_namemax.set(Int.MAX_VALUE) // the limit from index ability

        return 0
    }
}
