package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.Operations

class ListEntryCommand : CliktCommand(
    name = "list",
    help = "List all entries in the given storage"
) {
    override fun run() {
        val storage = Global.getStorage()
        Operations.listEntry(storage)
            .forEach { it.printDetails() }
    }
}
