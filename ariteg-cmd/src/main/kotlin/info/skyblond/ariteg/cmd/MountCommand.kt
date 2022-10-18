package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.cmd.fuse.AritegFS
import jnr.ffi.Platform
import mu.KotlinLogging
import java.io.File
import java.nio.file.Paths

class MountCommand : CliktCommand(
    name = "mount",
    help = "mount as FUSE"
) {
    // TODO mount path, uid, gid, dir mask, file mask
//    private val files: List<File> by argument(name = "Path", help = "Path to content to be uploaded")
//        .file(
//            mustExist = true,
//            canBeFile = true,
//            canBeDir = true
//        ).multiple()

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Mount"))
        val memfs = AritegFS()
        try {
            val path: String = when (Platform.getNativePlatform().os) {
                Platform.OS.WINDOWS -> "M:\\"
                else -> "/tmp/mntm"
            }
            echo("Mounting... Press CTRL+C to exit")
            memfs.mount(
                Paths.get(path), true, false, arrayOf()
            )
        } finally {
            memfs.umount()
        }
    }
}
