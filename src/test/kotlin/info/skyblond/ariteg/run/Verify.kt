package info.skyblond.ariteg.run

import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.run.utils.useStorageSafe
import java.util.concurrent.CompletableFuture

fun main() {
    useStorageSafe { logger, storage ->
        logger.info { "Listing entries..." }
        Operations.listEntry(storage).forEach { entry ->
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
                if (index % 100 == 0) {
                    logger.info { "${index / 1000.0}K blobs are checked" }
                }
            }
        }
        logger.info { "Done" }
    }
}
