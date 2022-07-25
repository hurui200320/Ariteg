package info.skyblond.ariteg.run

import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.run.utils.useStorageSafe

fun main() {
    val id = ""

    useStorageSafe { logger, storage ->
        logger.info { "Finding entry..." }
        val entry = Operations.listEntry(storage)
            .find { it.id == id } ?: error("Entry not found")

        logger.info { "Removing entry ${entry.id} (${entry.name})" }
        Operations.deleteEntry(entry, storage)

        logger.info { "Done" }
    }
}
