package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.cmd.CmdContext
import mu.KotlinLogging

class GCCommand : CliktCommand(
    name = "gc",
    help = "Remove all unused objects"
) {
    private val logger = KotlinLogging.logger("GC")

    override fun run() {
        logger.info { "Starting gc..." }
        Operations.gc(CmdContext.storage)
        logger.info { "Done" }
    }
}
