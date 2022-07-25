package info.skyblond.ariteg.run

import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.run.utils.printDetails
import info.skyblond.ariteg.run.utils.useStorageSafe

fun main() {
    useStorageSafe { logger, storage ->
        logger.info { "Listing entries..." }

        Operations.listEntry(storage)
            .forEach { it.printDetails() }

        logger.info { "Done" }
    }
}
