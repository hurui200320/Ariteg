package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import mu.KotlinLogging

class IntegrityCheckCommand : CliktCommand(
    name = "ic",
    help = "Make sure all blobs are correct, and delete corrupted blobs"
) {
    private val deleting: Boolean by argument(name = "DEL_FLAG", help = "Delete broken blobs")
        .convert { it.toBoolean() }
        .default(false)

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("IntegrityCheck"))
        CmdContext.integrityCheck(deleting)
    }
}
