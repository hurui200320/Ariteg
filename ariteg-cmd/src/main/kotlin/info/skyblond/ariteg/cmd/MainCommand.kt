package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import info.skyblond.ariteg.Operations

class MainCommand : CliktCommand(
    name = "ariteg-cmd"
) {
    init {
        subcommands(
            // CRUD
            ListEntryCommand(),
            MountCommand(),
            UploadCommand(),
            DownloadCommand(),
            RemoveEntryCommand(),
            FindCommand(),
            // Maintenance
            GCCommand(),
            IntegrityCheckCommand(),
            VerifyEntryCommand(),
            StatusCommand(),
        )
    }

    override fun run() {
    }
}

fun main(args: Array<String>) = MainCommand().main(args)
