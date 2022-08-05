package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import mu.KotlinLogging

class IntegrityCheckCommand : CliktCommand(
    name = "ic",
    help = "Make sure all blobs are correct, and delete corrupted blobs"
) {
    init {
        CmdContext.setLogger(KotlinLogging.logger("IntegrityCheck"))
    }

    override fun run() = CmdContext.integrityCheck()
}
