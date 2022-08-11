package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.Operations
import mu.KotlinLogging
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

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
        CmdContext.setLogger(KotlinLogging.logger("Script"))
    }

    override fun run() {
        val cx = Context.enter()
        try {
            val scope: Scriptable = cx.initStandardObjects()

            Context.javaToJS(CmdContext, scope)
                .let { ScriptableObject.putProperty(scope, "context", it) }

            Context.javaToJS(CmdContext.logger, scope)
                .let { ScriptableObject.putProperty(scope, "logger", it) }

            Context.javaToJS(Operations, scope)
                .let { ScriptableObject.putProperty(scope, "operations", it) }

            @Suppress("unused")
            Context.javaToJS(object {
                fun createFile(path: String): File = File(path)
            }, scope)
                .let { ScriptableObject.putProperty(scope, "utils", it) }


            scriptFile.bufferedReader().use {
                cx.evaluateReader(scope, it, scriptFile.name, 0, null)
            }
        } finally {
            Context.exit()
        }
    }
}
