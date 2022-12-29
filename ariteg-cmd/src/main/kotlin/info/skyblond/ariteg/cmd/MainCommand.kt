package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class MainCommand : CliktCommand() {
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
        // nop
    }
}

fun main(args: Array<String>) = MainCommand().main(args)
