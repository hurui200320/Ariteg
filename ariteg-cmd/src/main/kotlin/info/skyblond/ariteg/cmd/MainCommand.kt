package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import info.skyblond.ariteg.storage.FileStorage
import info.skyblond.ariteg.storage.MinioStorage
import io.minio.MinioClient
import java.io.File
import java.util.*
import java.util.concurrent.ForkJoinPool

class MainCommand : CliktCommand() {
    private val connectStringEnv = "ARITEG_CONNECT_STR"
    private val encryptionKeyEnv = "ARITEG_ENCRYPTION_KEY"

    private val connectString: String
        get() = System.getenv(connectStringEnv) ?: error("Env `${connectStringEnv}` not set")
    private val keyBase64: String
        get() = System.getenv(encryptionKeyEnv)
            ?: "".also { System.err.println("Env `${encryptionKeyEnv}` not set, no encryption") }


    init {
        subcommands(
            ListEntryCommand(),
            RemoveEntryCommand(),
            GCCommand(),
            IntegrityCheckCommand(),
            VerifyEntryCommand(),
            UploadCommand(),
            DownloadCommand(),
            RunJSCommand(),
            MountCommand(),
            StatusCommand(),
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

        echo("ForkJoinPool threads: ${ForkJoinPool.commonPool().parallelism}")
    }
}

fun main(args: Array<String>) = MainCommand().main(args)
