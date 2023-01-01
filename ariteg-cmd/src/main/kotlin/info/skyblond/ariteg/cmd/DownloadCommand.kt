package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.Operations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

class DownloadCommand : CliktCommand(
    name = "download",
    help = "Download entries from the storage"
) {
    private val logger = KotlinLogging.logger("Download")
    private val names: List<String> by argument(
        name = "name", help = "Entry name, can be multiple. " +
                "Leave empty means download all"
    ).multiple()
    private val folder: File by option("-p", "--path", help = "Path to download folder")
        .file(mustExist = false, canBeFile = false, canBeDir = true)
        .required()

    override fun run() {
        val workingQueue = names.ifEmpty {
            CmdContext.storage.listEntry().map { it.name }.toList()
        }
        val downloaded = mutableSetOf<String>()
        runBlocking {
            Operations.listEntry(CmdContext.storage)
                .filter { it.name in workingQueue }
                .toList()
                .map { entry ->
                    runBlocking { Operations.waitForMemory() }
                    async {
                        logger.info { "Start downloading ${entry.name}..." }
                        Operations.restore(entry, CmdContext.storage, folder)
                        logger.info { "Finished: ${entry.name}" }
                        downloaded.add(entry.name)
                    }
                }.awaitAll()
        }
        downloaded.forEach {
            echo("Downloaded: $it")
        }
        (workingQueue - downloaded).forEach {
            echo("Entry not found: $it", err = true)
        }
    }
}
