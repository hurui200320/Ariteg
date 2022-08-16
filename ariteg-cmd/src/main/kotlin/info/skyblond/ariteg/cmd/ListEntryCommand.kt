package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import mu.KotlinLogging

class ListEntryCommand : CliktCommand(
    name = "ls",
    help = "List all entries in the given storage"
) {
    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("LS"))
        CmdContext.listEntry().forEach { it.printDetails() }
    }
}
