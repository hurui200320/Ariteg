package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import info.skyblond.ariteg.Operations

class RemoveEntryCommand : CliktCommand(
    name = "rm",
    help = "Remove the given entry"
) {
    private val names: List<String> by argument(name = "Name", help = "Entry names").multiple()

    override fun run() {
        val deleted = mutableSetOf<String>()
        Operations.listEntry(CmdContext.storage).forEach {
            if (it.name in names) {
                Operations.deleteEntry(it, CmdContext.storage)
                echo("Deleted: ${it.name}")
                deleted.add(it.name)
            }
        }
        (names - deleted).forEach {
            echo("Not found: $it", err = true)
        }
    }
}
