package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.obj.Entry
import info.skyblond.ariteg.storage.obj.Link
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong

class VerifyEntryCommand : CliktCommand(
    name = "verify",
    help = "Make sure the given entry is readable. " +
            "This command will essentially download the whole entry and discard it. " +
            "It will make sure the entry is fine, or will throw an error. " +
            "Careful when using with AWS S3 or other billed by usage services."
) {
    private val logger = KotlinLogging.logger("Verify")

    private val names: List<String> by argument(
        name = "names", help = "Entry names, can be multiple. Empty means check all entries"
    ).multiple()

    override fun run() = runBlocking {
        logger.info { "Verifying entries..." }
        val taskChannel = Channel<Entry>(Channel.UNLIMITED)
        val taskCounter = AtomicLong(0)
        repeat(Runtime.getRuntime().availableProcessors()) {
            launch {
                for (entry in taskChannel) {
                    logger.info { "Start checking ${entry.name}" }
                    Operations.resolve(entry, CmdContext.storage)
                        .filter { it.type == Link.Type.BLOB }
                        .forEachIndexed { index, link ->
                            Operations.waitForMemory()
                            CmdContext.storage.read(link)
                            if (index % 1000 == 0) {
                                logger.info { "${entry.name}: Checked ${index / 1000}K blobs" }
                            }
                        }
                    taskCounter.decrementAndGet()
                    logger.info { "Finished ${entry.name}" }
                }
            }
        }

        Operations.listEntry(CmdContext.storage).forEach { entry ->
            if (names.isEmpty() || entry.name in names) {
                taskCounter.incrementAndGet()
                taskChannel.send(entry)
            }
        }
        taskChannel.close()
        while (taskCounter.get() != 0L) {
            delay(1000)
        }
        logger.info { "Done" }
    }
}
