package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.Operations
import mu.KotlinLogging

class GCCommand : CliktCommand(
    name = "gc",
    help = "Remove all unused objects"
) {
    private val logger = KotlinLogging.logger("GC")

    override fun run() {
        val storage = Global.getStorage()
        logger.info { "Starting gc..." }
        Operations.gc(storage)
        logger.info { "Done" }
    }
}
