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

    init {
        CmdContext.setLogger(KotlinLogging.logger("GC"))
    }

    override fun run() = CmdContext.removeEntry(ids)
}
