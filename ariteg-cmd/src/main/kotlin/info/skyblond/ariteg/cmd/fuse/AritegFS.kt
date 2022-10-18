package info.skyblond.ariteg.cmd.fuse

import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link
import info.skyblond.ariteg.TreeObject
import info.skyblond.ariteg.cmd.CmdContext
import info.skyblond.ariteg.cmd.asOtcInt
import info.skyblond.ariteg.cmd.calculateFileSize
import jnr.ffi.Pointer
import jnr.posix.FileStat.*
import ru.serce.jnrfuse.ErrorCodes
import ru.serce.jnrfuse.FuseFillDir
import ru.serce.jnrfuse.FuseStubFS
import ru.serce.jnrfuse.struct.FileStat
import ru.serce.jnrfuse.struct.FuseFileInfo
import ru.serce.jnrfuse.struct.Statvfs
import java.io.File
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
                var link = CmdContext.storage.listEntry().find { it.id == entryId }?.link
                    ?: return (null to -ErrorCodes.ENOENT())
                        .also { logger.error { "Path not found: $path" } }

                for (i in 1 until seg.size) {
                    val currentFindingEntryName = seg[i]
                    if (link.type != Link.Type.TREE) {
                        // if not folder, can't search
                        return (null to -ErrorCodes.ENOENT())
                            .also { logger.error { "Excepting TREE, but not TREE, during parsing $path" } }
                    }
                    val tree = CmdContext.storage.read(link).get() as TreeObject
                    // update link with result
                    link = tree.content.find { it.name == currentFindingEntryName }
                        ?: return (null to -ErrorCodes.ENOENT())
                            .also { logger.error { "Element not found during parsing: $path" } }
                }
                logger.info { "Parsing $path result: $link" }
                return link to 0
            }

    private fun mapEntryToFileName(entry: Entry): String = entry.id + " " + entry.name

    private fun nockOffWritePermission(permission: Int): Int =
        permission and S_IWUSR.inv() and S_IWGRP.inv() and S_IWOTH.inv()

    override fun getattr(path: String, stat: FileStat): Int {
        logger.info { "get attr $path" }
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
                // TODO: sync birthtime, ctime, mtime with entry
//            stat.st_birthtime
//            stat.st_mtim
//            stat.st_ctim
                stat.st_size.set(calculateFileSize(link))
            }
        }
        return 0 // return success
    }

    /**
     * The readdir implementation ignores the offset parameter,
     * and passes zero to the filler function's offset.
     * The filler function will not return '1' (unless an error happens),
     * so the whole directory is read in a single readdir operation.
     * This works just like the old getdir() method.
     * */
    override fun readdir(
        path: String, buf: Pointer, filler: FuseFillDir, offset: Long, fi: FuseFileInfo
    ): Int {
        logger.info { "read dir: $path" }

        // fixed two entries
        filler.apply(buf, ".", null, 0)
        filler.apply(buf, "..", null, 0)
        if (path == "/") {
            // root folder
            CmdContext.storage.listEntry()
                .map { mapEntryToFileName(it) }
                .onEach { if (it.contains(File.separator)) logger.error { "Found separator in filename: $it, path: $path" } }
                .forEach { filler.apply(buf, it, null, 0) }
        } else {
            // everything else
            val (link, errorCode) = resolvePath(path)
            if (link == null) return errorCode
            // the returned link should be a tree
            if (link.type != Link.Type.TREE) return -ErrorCodes.ENOENT()
            logger.info { "Loading tree for folder content" }
            // fetch content and list
            val tree = try { // TODO use new version repo
                // TODO add cache layer, r/w object using that layer
                CmdContext.storage.read(link).get() as TreeObject
            } catch (t: Throwable) {
                logger.error(t) { "????" }
                error("!!!!")
            }
            logger.info { "Tree loaded" }
            // use set to make sure every name is distinct
            tree.content.map { it.name!! }.toSet()
                .onEach { logger.info { "Listing sub: $it" } }
                .onEach { if (it.contains(File.separator)) logger.error { "Found separator in filename: $it, path: $path" } }
                .forEach { filler.apply(buf, it, null, 0) }

        }
        return 0 // return success
    }

    override fun read(
        path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo
    ): Int {
        // TODO
        val data = path.encodeToByteArray()
        val readBytesCount = min(data.size - offset, size).toInt()
        buf.put(0, data, offset.toInt(), readBytesCount)
        return readBytesCount // return the number of actual bytes read
    }

    override fun open(path: String?, fi: FuseFileInfo?): Int {
        // TODO build index in cache
        return super.open(path, fi)
    }

    override fun statfs(path: String, stbuf: Statvfs): Int {
        logger.info("statfs $path")
        stbuf.f_bsize.set(1024) // 1KB per block
        stbuf.f_frsize.set(1024) // 1KB per unit
        stbuf.f_bfree.set(0) // no free space
        stbuf.f_bavail.set(0) // no free space
        stbuf.f_blocks.set(1234) // TODO memory cache size?
        stbuf.f_files.set(2345) // TODO cache obj counts?
        stbuf.f_ffree.set(0) // no free space
        stbuf.f_favail.set(0) // no free space
        stbuf.f_fsid.set(fsId) // use mounting time as id
        stbuf.f_flag.set((Statvfs.ST_RDONLY or Statvfs.ST_IMMUTABLE).toLong())
        stbuf.f_namemax.set(Int.MAX_VALUE) // the limit from index ability

        return 0
    }

    override fun destroy(initResult: Pointer?) {
        logger.info { "Destroy FUSE mount..." }
    }
}
