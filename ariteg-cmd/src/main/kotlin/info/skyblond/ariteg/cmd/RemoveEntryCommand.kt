package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import mu.KotlinLogging

class RemoveEntryCommand : CliktCommand(
    name = "rm",
    help = "Remove the given entry"
) {
    private val ids: List<String> by argument(name = "ID", help = "Entry ids").multiple()

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Remove"))
        CmdContext.removeEntry(ids)
    }
}
