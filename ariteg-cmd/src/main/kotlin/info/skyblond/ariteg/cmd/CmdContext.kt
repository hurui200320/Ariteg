package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.storage.FileStorage
import info.skyblond.ariteg.storage.MinioStorage
import info.skyblond.ariteg.storage.Storage
import io.minio.MinioClient
import mu.KotlinLogging
import java.io.File
import java.util.*

object CmdContext {
    private val logger = KotlinLogging.logger { }

    private const val ENV_CONNECT_STRING = "ARITEG_CONNECT_STR"
    private const val ENV_ENCRYPTION_KEY = "ARITEG_ENCRYPTION_KEY"

    private fun parseKeyBase64(keyBase64: String): ByteArray? =
        if (keyBase64.isBlank()) null else Base64.getDecoder().decode(keyBase64)

    @JvmStatic
    val storage: Storage = kotlin.run {
        val connectString: String = System.getenv(ENV_CONNECT_STRING)
            ?: error("Env `$ENV_CONNECT_STRING` not set")
        val keyBase64: String = System.getenv(ENV_ENCRYPTION_KEY)
            ?: "".also { logger.warn { "Env `$ENV_ENCRYPTION_KEY` not set, no encryption" } }

        when {
            connectString.startsWith("file://") -> {
                // something like "file:///mnt/something"
                val path = connectString.removePrefix("file://")
                val file = File(path)
                FileStorage(file, parseKeyBase64(keyBase64))
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
                MinioStorage(client, bucketName, parseKeyBase64(keyBase64))
            }

            else -> error("Unknown schema in $connectString")
        }
    }
}
