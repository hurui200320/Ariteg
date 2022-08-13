package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.FileStorage
import info.skyblond.ariteg.storage.MinioStorage
import io.minio.MinioClient
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.*

class  RunJSCommand : CliktCommand(
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
                fun createFile(path: String): File = File(path).also { FileUtils.forceMkdirParent(it) }
                fun createFile(file: File, name: String): File = File(file, name)
                fun createFolder(path: String): File = File(path).also { FileUtils.forceMkdir(it) }
                fun deleteFile(file: File) = FileUtils.forceDelete(file)
                fun setDeleteFileOnExit(file: File) = FileUtils.forceDeleteOnExit(file)

                fun createFileStorage(file: File, key: String): FileStorage =
                    FileStorage(file, if (key.isBlank()) null else Base64.getDecoder().decode(key))

                fun createMinioStorage(
                    host: String, accessKey: String, secretKey: String,
                    bucketName: String, key: String
                ): MinioStorage = MinioStorage(
                    MinioClient.builder()
                        .endpoint(host)
                        .credentials(accessKey, secretKey)
                        .build(),
                    bucketName,
                    if (key.isBlank()) null else Base64.getDecoder().decode(key)
                )

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
