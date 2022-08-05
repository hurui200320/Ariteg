package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.options.required
import info.skyblond.ariteg.storage.FileStorage
import info.skyblond.ariteg.storage.MinioStorage
import io.minio.MinioClient
import java.io.File
import java.util.*

class MainCommand : CliktCommand() {
    private val connectString: String by option(
        "-c", "--connect-string",
        help = "Connect string",
        metavar = "STRING",
        envvar = "ARITEG_CONNECT_STRING",
    ).required()

    private val keyBase64: String by option(hidden = true).prompt(
        "Base64 encoded key",
        default = "",
        hideInput = true
    )

    init {
        subcommands(
            ListEntryCommand(),
            RemoveEntryCommand(),
            GCCommand(),
            IntegrityCheckCommand(),
            VerifyEntryCommand(),
            UploadCommand(),
            DownloadCommand(),
            RunJSCommand()
        )
    }

    private fun parseKeyBase64(): ByteArray? =
        if (keyBase64.isBlank()) null else Base64.getDecoder().decode(keyBase64)

    override fun run() {
        // parse connect string
        val storage = when {
            connectString.startsWith("file://") -> {
                // something like "file:///mnt/something"
                val path = connectString.removePrefix("file://")
                val file = File(path)
                FileStorage(file, parseKeyBase64())
            }
            connectString.startsWith("minio://") -> {
                // something like "minio://access@secret:host:port/bucketName"
                val str = connectString.removePrefix("minio://")
                val (url, bucketName) = str.split("/")
                    .also { require(it.size == 2) { "Failed to resolve url and bucket name from: $connectString" } }
                // now url is "access@secret:host:port"
                val (credential, host) = url.split(":").let {
                    it[0] to it.drop(1).joinToString(":")
                }
                val (accessKey, secretKey) = credential.split("@")
                    .also { require(it.size == 2) { "Failed to resolve access key from: $connectString" } }
                val client = MinioClient.builder()
                    .endpoint(host)
                    .credentials(accessKey, secretKey)
                    .build()
                MinioStorage(client, bucketName, parseKeyBase64())
            }

            else -> error("Unknown schema in $connectString")
        }
        CmdContext.setStorage(storage)
    }
}

fun main(args: Array<String>) = MainCommand().main(args)
