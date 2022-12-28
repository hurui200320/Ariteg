package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.cmd.fuse.RandomAccessCache
import info.skyblond.ariteg.storage.obj.Link
import java.time.format.DateTimeFormatter

class ListEntryCommand : CliktCommand(
    name = "ls",
    help = "List all entries in the given storage"
) {
    override fun run() {
        val entries = Operations.listEntry(CmdContext.storage)
        println("total ${entries.count()}")
        entries.forEach {
            // print type
            print(
                when (it.root.type) {
                    Link.Type.BLOB -> "b"
                    Link.Type.LIST -> "l"
                    Link.Type.TREE -> "t"
                } + " "
            )
            // print raw size
            print(RandomAccessCache.calculateFileSize(it.root).toString() + " ")
            // print date
            print(DateTimeFormatter.ISO_INSTANT.format(it.ctime.toInstant()) + " ")
            // print name
            println(it.name)
        }
    }
}
