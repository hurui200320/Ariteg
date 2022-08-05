package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import mu.KotlinLogging

class ListEntryCommand : CliktCommand(
    name = "ls",
    help = "List all entries in the given storage"
) {
    init {
        CmdContext.setLogger(KotlinLogging.logger("LS"))
    }

    override fun run() = CmdContext.listEntry().forEach { it.printDetails() }
}
