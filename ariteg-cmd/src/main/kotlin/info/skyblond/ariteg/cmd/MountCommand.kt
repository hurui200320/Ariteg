package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import info.skyblond.ariteg.cmd.fuse.AritegFS
import mu.KotlinLogging
import java.nio.file.Paths
import java.util.concurrent.ForkJoinPool

class MountCommand : CliktCommand(
    name = "mount",
    help = "mount as FUSE"
) {
    private val mountPath: String by argument(name = "path", help = "Path to mount the FUSE")

    private val mountUid: String? by option("-u", "--uid", help = "Owner uid")
    private val mountGid: String? by option("-g", "--gid", help = "Owner gid")
    private val mountDirMask: String by option("-d", "--dir", help = "Dir mask (UGO)").default("0755")
    private val mountFileMask: String by option("-f", "--file", help = "Dir mask (UGO)").default("0644")

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Mount"))
        val memfs = AritegFS(
            uid = mountUid?.toLong(),
            gid = mountGid?.toLong(),
            dirMask = mountDirMask.asOtcInt(),
            fileMask = mountFileMask.asOtcInt()
        )
        try {
            echo("Mounting to `$mountPath` Press CTRL+C to exit")
            memfs.mount(Paths.get(mountPath), true, false)
        } finally {
            memfs.umount()
        }
    }
}
