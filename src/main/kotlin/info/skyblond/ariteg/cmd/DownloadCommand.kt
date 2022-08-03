package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.Operations
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
    private val logger = KotlinLogging.logger("Upload")

    override fun run() {
        val storage = Global.getStorage()
        logger.info { "Finding entry..." }
        val entry = Operations.listEntry(storage)
            .find { it.id == id } ?: error("Entry not found")
        logger.info { "Start downloading..." }
        Operations.restore(entry, storage, file)

        logger.info { "Done" }
    }
}
