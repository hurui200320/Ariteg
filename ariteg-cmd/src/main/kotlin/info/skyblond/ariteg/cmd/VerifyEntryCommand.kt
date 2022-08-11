package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import mu.KotlinLogging

class VerifyEntryCommand : CliktCommand(
    name = "verify",
    help = "Make sure the given entry is readable"
) {
    private val ids: List<String> by argument(
        name = "ID", help = "Entry ids. Empty means check all entries"
    ).multiple()

    init {
        CmdContext.setLogger(KotlinLogging.logger("Verify"))
    }

    override fun run() = CmdContext.verifyEntry(ids)
}
