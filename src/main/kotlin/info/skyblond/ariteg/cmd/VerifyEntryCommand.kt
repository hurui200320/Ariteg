package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import info.skyblond.ariteg.Operations
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture

class VerifyEntryCommand : CliktCommand(
    name = "verify",
    help = "Make sure the given entry is readable"
) {
    private val ids: List<String> by argument(
        name = "ID", help = "Entry ids. Empty means check all entries"
    ).multiple()
    private val logger = KotlinLogging.logger("Verify")

    override fun run() {
        val storage = Global.getStorage()
        logger.info { "Listing entries..." }
        Operations.listEntry(storage).forEach { entry ->
            if (ids.isNotEmpty() && entry.id !in ids) {
                return@forEach
            }
            logger.info { "Verifying entry: ${entry.id} (${entry.name})" }
            val blobs = Operations.resolve(entry, storage)
            logger.info { "Verifying ${blobs.size / 1000.0}K blobs..." }
            val semaphore = Operations.getLimitingSemaphore()
            blobs.map {
                CompletableFuture.runAsync {
                    semaphore.acquire()
                    storage.read(it)
                }.exceptionally { semaphore.release(); throw it }
                    .thenRun { semaphore.release() }
            }.forEachIndexed { index, completableFuture ->
                completableFuture.get()
                if (index % 1000 == 0) {
                    logger.info { "${index / 1000}K blobs are checked" }
                }
            }
        }
        logger.info { "Done" }
    }
}
