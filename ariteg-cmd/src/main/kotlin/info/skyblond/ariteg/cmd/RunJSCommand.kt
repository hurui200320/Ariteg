package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import mu.KotlinLogging
import java.io.File
import javax.script.ScriptEngineManager

class RunJSCommand : CliktCommand(
    name = "run",
    help = "run the given JavaScript file"
) {
    private val scriptFile: File by argument(name = "Path", help = "Path to js script")
        .file(
            mustExist = true,
            canBeFile = true,
            canBeDir = false,
            mustBeReadable = true
        )

    init {
        System.setProperty("nashorn.args", "--language=es6")
    }

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Script"))
        val engineManager = ScriptEngineManager()
        val engine = engineManager.getEngineByName("nashorn")!!
        scriptFile.bufferedReader().use {
            engine.eval(it)
        }
    }
}
