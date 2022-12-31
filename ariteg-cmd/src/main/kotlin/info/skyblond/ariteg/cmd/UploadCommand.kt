package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.Operations
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

class UploadCommand : CliktCommand(
    name = "upload",
    help = "Upload a file or a folder to the storage"
) {
    private val logger = KotlinLogging.logger("Upload")

    private val files: List<File> by argument(name = "Path", help = "Path to content to be uploaded")
        .file(mustExist = true, canBeFile = true, canBeDir = true)
        .multiple()

    override fun run() {
        runBlocking {
            files.map { file ->
                logger.info { "Uploading ${file.canonicalPath}" }
                val entry = Operations.digest(file, getSlicer(), CmdContext.storage)
                logger.info { "Finished ${entry.name}" }
                entry
            }.forEach { entry ->
                echo("Uploaded: ${entry.name}")
            }
        }

        logger.info { "Done" }
    }
}
