package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.Link
import io.minio.*
import io.minio.errors.ErrorResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override fun listEntryPath(): Sequence<String> = sequence {
        val prefix = "entry/"
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
            // get full name
            yieldAll(batch)
            if (batch.isNotEmpty()) {
                // not finished, keep getting keys
                startAfter = batch.last()
            } else {
                // finished, exit loop
                break
            }
        }
    }

    override fun listObjects(type: Link.Type): Sequence<Link> = sequence {
        val prefix = "${type.name.lowercase()}/"
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
            // parse to link
            yieldAll(batch.map { Link(it.drop(prefix.length), type, -1) })
            if (batch.isNotEmpty()) {
                // not finished, keep getting keys
                startAfter = batch.last()
            } else {
                // finished, exit loop
                break
            }
        }
    }

    override fun close() {
        // nop
    }

    override suspend fun internalWrite(path: String, data: ByteArray) {
        try {
            withContext(Dispatchers.IO) {
                minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(path)
                        .build()
                )
            }
            throw ObjectAlreadyExistsException(null)
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() != "NoSuchKey") {
                // not object not found
                throw e
            }
        }

        withContext(Dispatchers.IO) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(path)
                    .stream(data.inputStream(), data.size.toLong(), -1)
                    .build()
            )
        }
    }

    override suspend fun internalRead(path: String): ByteArray {
        try {
            return withContext(Dispatchers.IO) {
                minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(path)
                        .build()
                ).use { it.readAllBytes() }
            }
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == "NoSuchKey") {
                // not object not found
                throw ObjectNotFoundException(e)
            } else {
                throw e
            }
        }
    }

    override suspend fun internalDelete(path: String) {
        withContext(Dispatchers.IO) {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(path)
                    .build()
            )
        }
    }
}
