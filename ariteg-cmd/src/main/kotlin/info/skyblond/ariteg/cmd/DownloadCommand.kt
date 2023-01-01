package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import mu.KotlinLogging
import java.io.File

class DownloadCommand : CliktCommand(
    name = "download",
    help = "Download a entry from the storage"
) {
    private val id: String by argument(name = "ID", help = "Entry id")
    private val file: File by argument(name = "Path", help = "Path to root download folder")
        .file(
            mustExist = false,
            canBeFile = false,
            canBeDir = true
        )

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Upload"))
        CmdContext.download(id, file)
    }
}
