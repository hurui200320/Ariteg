package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import groovy.lang.Binding
import groovy.lang.GroovyShell
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.obj.Entry
import info.skyblond.ariteg.storage.obj.Link
import kotlinx.coroutines.runBlocking
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class FindCommand : CliktCommand(
    name = "find",
    help = "Find certain entries and apply the operation"
) {
    private val query: String by argument(name = "Query", help = "The query written in groovy, must return bool,")
        .default("true")

    private val download: File? by option("-d", "--download-path", help = "Download entry into the path")
        .file(mustExist = false, canBeFile = false, canBeDir = true)

    private val delete: Boolean by option("-r", "--rm", help = "Remove the entry")
        .flag("--no-rm", default = false)

    override fun run() {
        // prepare the groovy scripting engine
        val sh = GroovyShell(
            Binding().apply {
                // Operations might take time, but the time would be frozen,
                // like, entry#1 take 1hr to process, then apply the query to entry#2,
                // if the query is time-dependent, then it might be screw up,
                // so we offer a static "now".
                // If they need real-time, just use ZonedDateTime.now()
                setVariable("now", ZonedDateTime.now())
            },
            CompilerConfiguration().apply {
                addCompilationCustomizers(SecureASTCustomizer().apply {
                    // no method def
                    isMethodDefinitionAllowed = false
                    // no closure def
                    isClosuresAllowed = false
                    // no package
                    isPackageAllowed = false
                    // to work with date, all you need is those classes
                    allowedImports = listOf(
                        ZonedDateTime::class.java.name,
                        ZoneId::class.java.name,
                    )
                    allowedStarImports = listOf()
                    allowedStaticImports = listOf()
                    allowedStaticStarImports = listOf(
                        // BLOB, LIST, TREE
                        Link.Type::class.java.name,
                    )
                    setAllowedReceiversClasses(
                        listOf(
                            Entry::class.java,
                            String::class.java,
                            Link::class.java,
                            Link.Type::class.java,
                            ZonedDateTime::class.java,
                            ZoneId::class.java,
                            Math::class.java,
                            Any::class.java
                        )
                    )
                })
                addCompilationCustomizers(ImportCustomizer().apply {
                    addImports(
                        ZonedDateTime::class.java.canonicalName,
                        ZoneId::class.java.canonicalName
                    )
                    addStaticStars(
                        // so we can use BLOB, LIST, TREE
                        Link.Type::class.java.canonicalName
                    )
                })
            }
        )
        // for each entry
        CmdContext.storage.listEntry().forEach {
            sh.setVariable("entry", it)
            val result = sh.evaluate(query)
            require(result is Boolean) { "The script gives non-bool result: ${result::class.java.name}" }
            if (result) {
                echo("Selected entry: ${it.name}(${it.root.type}, at ${it.ctime})")
                if (download != null) {
                    echo("Downloading to $download")
                    runBlocking { Operations.restore(it, CmdContext.storage, download!!) }
                }
                if (delete) {
                    echo("Deleting...")
                    Operations.deleteEntry(it, CmdContext.storage)
                }
            }
        }
    }
}
