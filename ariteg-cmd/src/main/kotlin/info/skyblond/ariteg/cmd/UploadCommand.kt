package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.file
import mu.KotlinLogging
import java.io.File

class UploadCommand : CliktCommand(
    name = "upload",
    help = "Upload a file or a folder to the storage"
) {
    private val files: List<File> by argument(name = "Path", help = "Path to content to be uploaded")
        .file(
            mustExist = true,
            canBeFile = true,
            canBeDir = true
        ).multiple()

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Upload"))
        CmdContext.upload(files)
    }
}
