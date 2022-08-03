package info.skyblond.ariteg.storage

import io.minio.*
import io.minio.errors.ErrorResponseException
import mu.KotlinLogging


class MinioStorage(
    private val minioClient: MinioClient,
    private val bucketName: String,
    override val key: ByteArray? = null
) : AbstractStorage<String>() {
    private val logger = KotlinLogging.logger("MinioStorage")

    init {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
        }
        logger.info { "Using bucket: $bucketName" }

        if (key != null) {
            require(key.size == 32) { "The key must be 256bits" }
        }
    }

    override fun mapToPath(type: String, name: String): String {
        return "${type.lowercase()}/$name"
    }

    override fun internalWrite(path: String, data: ByteArray) {
        try {
            minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(path)
                    .build()
            )
            throw ObjectAlreadyExistsException()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() != "NoSuchKey") {
                // not object not found
                throw e
            }
        }

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(path)
                .stream(data.inputStream(), data.size.toLong(), -1)
                .build()
        )
    }

    override fun internalRead(path: String): ByteArray {
        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .`object`(path)
                .build()
        ).use { it.readAllBytes() }
    }

    override fun internalDelete(path: String) {
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .`object`(path)
                .build()
        )
    }

    override fun getParentPath(path: String): String {
        return path.split("/").dropLast(1).joinToString("/")
    }

    override fun listHashInPath(parentPath: String): Set<String> {
        val prefix = "${parentPath}/"
        val keys = HashSet<String>()
        var startAfter = ""
        while (true) {
            val resp = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .startAfter(startAfter)
                    .build()
            )
            // get path
            val batch = resp.map { it.get().objectName() }
            // get hash, aka, the name. not the full path
            keys.addAll(batch.map { it.removePrefix(prefix) })
            if (batch.isNotEmpty()) {
                // not finished, keep getting keys
                startAfter = batch.last()
            } else {
                // finished, exit loop
                break
            }
        }
        return keys
    }

    override fun listPathInPath(parentPath: String): List<String> {
        val prefix = "${parentPath}/"
        val paths = mutableListOf<String>()
        var startAfter = ""
        while (true) {
            val resp = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .startAfter(startAfter)
                    .build()
            )
            // get path
            val batch = resp.map { it.get().objectName() }
            // get hash, aka, the name. not the full path
            paths.addAll(batch)
            if (batch.isNotEmpty()) {
                // not finished, keep getting keys
                startAfter = batch.last()
            } else {
                // finished, exit loop
                break
            }
        }
        return paths
    }
}
