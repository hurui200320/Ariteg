package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import mu.KotlinLogging

class GCCommand : CliktCommand(
    name = "gc",
    help = "Remove all unused objects"
) {
    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("GC"))
        CmdContext.gc()
    }
}
