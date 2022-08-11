package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import mu.KotlinLogging

class GCCommand : CliktCommand(
    name = "gc",
    help = "Remove all unused objects"
) {
    init {
        CmdContext.setLogger(KotlinLogging.logger("GC"))
    }

    override fun run() = CmdContext.gc()
}
