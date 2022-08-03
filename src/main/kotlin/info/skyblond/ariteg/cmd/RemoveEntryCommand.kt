package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import info.skyblond.ariteg.Operations
import mu.KotlinLogging

class RemoveEntryCommand : CliktCommand(
    name = "rm",
    help = "Remove the given entry"
) {
    private val ids: List<String> by argument(name = "ID", help = "Entry ids").multiple()
    private val logger = KotlinLogging.logger("GC")

    override fun run() {
        val storage = Global.getStorage()
        logger.info { "Listing entries..." }
        val entries = Operations.listEntry(storage)
        logger.info { "Deleting..." }

        ids.forEach { target ->
            entries.find { it.id == target }?.let { entry ->
                Operations.deleteEntry(entry, storage)
                logger.info { "Entry $target deleted" }
            } ?: logger.error { "Entry $target not found" }
        }
    }
}
