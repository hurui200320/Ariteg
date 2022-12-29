package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.obj.Entry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

class DownloadCommand : CliktCommand(
    name = "download",
    help = "Download a entry from the storage"
) {
    private val logger = KotlinLogging.logger("Download")
    private val names: List<String> by argument(name = "Names", help = "Entry id. Empty for all").multiple()
    private val folder: File by option("-p", "--path", help = "Path to root download folder")
        .file(mustExist = false, canBeFile = false, canBeDir = true)
        .required()

    override fun run() {
        val workingQueue = names.ifEmpty {
            CmdContext.storage.listEntry().map { it.name }.toList()
        }
        val downloaded = mutableSetOf<String>()
        runBlocking {
            val taskChannel = Channel<Entry>(Channel.UNLIMITED)
            repeat(Runtime.getRuntime().availableProcessors()) {
                launch {
                    for (entry in taskChannel) {
                        logger.info { "Start downloading ${entry.name}..." }
                        Operations.restore(entry, CmdContext.storage, folder)
                        logger.info { "Finished: ${entry.name}" }
                        downloaded.add(entry.name)
                    }
                }
            }

            Operations.listEntry(CmdContext.storage)
                .filter { it.name in workingQueue }
                .forEach { taskChannel.send(it) }
            taskChannel.close()
        }
        downloaded.forEach {
            echo("Downloaded: $it")
        }
        (workingQueue - downloaded).forEach {
            echo("Entry not found: $it", err = true)
        }
    }
}
