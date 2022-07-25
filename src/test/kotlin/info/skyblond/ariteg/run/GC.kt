package info.skyblond.ariteg.run

import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.run.utils.useStorageSafe

fun main() {
    useStorageSafe { logger, storage ->
        logger.info { "Starting gc..." }
        Operations.gc(storage)
        logger.info { "Done" }
    }
}
