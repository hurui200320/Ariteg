package info.skyblond.ariteg.run

import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.run.utils.useStorageSafe
import java.io.File

fun main() {
    val id = "1658596041771-6256093746350824316"
    val root = File("/mnt/user/temp/ariteg-restore")

    useStorageSafe { logger, storage ->
        logger.info { "Finding entry..." }
        val entry = Operations.listEntry(storage)
            .find { it.id == id } ?: error("Entry not found")
        logger.info { "Start downloading..." }
        Operations.restore(entry, storage, root)

        logger.info { "Done" }
    }
}
