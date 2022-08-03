package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.Operations
import mu.KotlinLogging

class IntegrityCheckCommand  : CliktCommand(
    name = "ic",
    help = "Make sure all blobs are correct, and delete corrupted blobs"
) {
    private val logger = KotlinLogging.logger("IntegrityCheck")

    override fun run() {
        val storage = Global.getStorage()
        logger.info { "Starting integrity check..." }
        Operations.integrityCheck(storage)
        logger.info { "Done" }
    }
}
