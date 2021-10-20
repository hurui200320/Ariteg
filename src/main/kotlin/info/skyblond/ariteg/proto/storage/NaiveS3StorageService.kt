package info.skyblond.ariteg.proto.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.objects.toMultihash
import io.ipfs.multihash.Multihash
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.InvalidObjectStateException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.StorageClass
import software.amazon.awssdk.services.s3.model.Tier
import software.amazon.awssdk.utils.Md5Utils
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A naive S3 storage is implement for basic usage. Advanced S3 features
 * are not support.
 * */
class NaiveS3StorageService(
    private val s3Client: S3Client,
    private val bucketName: String,
    private val primaryMultihashProvider: MultihashProvider,
    private val secondaryMultihashProvider: MultihashProvider,
    threadNum: Int = Runtime.getRuntime().availableProcessors(),
    queueSize: Int = 64,
    private val multihashToKeyMapper: (ObjectType, Multihash) -> String = { t, k ->
        t.name.lowercase() + "/" + k.toBase58()
    }
) : ProtoStorageService {
    init {
        // will throw exception if bucket not exists
        s3Client.headBucket { it.bucket(bucketName) }
    }

    /**
     * Choose what storage class to upload
     * */
    @Volatile
    var uploadStorageClass: StorageClass = StorageClass.STANDARD

    private val threadPool = ThreadPoolExecutor(
        threadNum, threadNum,
        0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(queueSize), ThreadPoolExecutor.CallerRunsPolicy()
    )

    override fun getPrimaryMultihashType(): Multihash.Type = primaryMultihashProvider.getType()

    override fun getSecondaryMultihashType(): Multihash.Type = secondaryMultihashProvider.getType()

    override fun storeProto(
        name: String,
        proto: AritegObject,
        check: (Multihash, Multihash) -> Boolean,
    ): Pair<AritegLink, CompletableFuture<Multihash?>> {
        // get raw bytes
        val (primaryMultihash, tempFile) = proto.toByteArray().let { rawBytes ->
            // calculate multihash and save content to file (save memory)
            primaryMultihashProvider.digest(rawBytes) to File.createTempFile("proto", ".tmp")
                .apply { writeBytes(rawBytes); deleteOnExit() }
        }

        // submit task
        val future = CompletableFuture.supplyAsync({
            val rawBytes = tempFile.readBytes()
            // calculate secondary hash
            val secondaryMultihash = secondaryMultihashProvider.digest(rawBytes)
            // run the check, return if we get false
            if (check(primaryMultihash, secondaryMultihash)) {
                // check pass, continue request
                s3Client.putObject(
                    {
                        it.bucket(bucketName)
                            .key(multihashToKeyMapper(proto.type, primaryMultihash))
                            .storageClass(uploadStorageClass)
                            .contentMD5(Md5Utils.md5AsBase64(rawBytes))
                    }, RequestBody.fromBytes(rawBytes)
                )
                primaryMultihash
            } else {
                null
            }.also { tempFile.delete() }
        }, threadPool)

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(ByteString.copyFrom(primaryMultihash.toBytes()))
            .setType(proto.type)
            .build() to future
    }

    override fun linkExists(link: AritegLink): Boolean =
        try {
            s3Client.headObject {
                it.bucket(bucketName)
                    .key(multihashToKeyMapper(link.type, link.multihash.toMultihash()))
            }
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    data class RestoreOptions(
        val days: Int,
        val tier: Tier = Tier.BULK // cheapest one as default
    ) : ProtoStorageService.PrepareOption

    /**
     * [option] must be [RestoreOptions].
     * */
    override fun prepareLink(link: AritegLink, option: ProtoStorageService.PrepareOption) {
        require(option is RestoreOptions) { "Expect ${RestoreOptions::class.java} but ${option::class.java}" }

        try {
            s3Client.restoreObject { builder ->
                builder.bucket(bucketName)
                    .key(multihashToKeyMapper(link.type, link.multihash.toMultihash()))
                    .restoreRequest {
                        it.days(option.days)
                            .tier(option.tier)
                    }
            }
        } catch (e: NoSuchKeyException) {
            throw ObjectNotFoundException(link)
        }

    }

    override fun linkAvailable(link: AritegLink): Boolean {
        val header = try {
            s3Client.headObject {
                it.bucket(bucketName)
                    .key(multihashToKeyMapper(link.type, link.multihash.toMultihash()))
            }
        } catch (e: NoSuchKeyException) {
            throw ObjectNotFoundException(link)
        }

        return if (header.storageClass() in listOf(
                StorageClass.DEEP_ARCHIVE, StorageClass.GLACIER
            )
        ) {
            // check the restore status. null means no request for restore
            val restore = header.restore()?.lowercase() ?: throw ObjectNotPreparedException(link)
            assert(restore.contains("ongoing-request=\"")) { "No target string found. S3 might upgrade the api. Check the code!" }
            val ongoingString = restore.split("ongoing-request=\"")[1].split("\"")[0]
            // ongoing = false means restoration is done.
            ongoingString == "false"
        } else {
            // normal layer, can access now
            true
        }
    }

    override fun loadProto(link: AritegLink): AritegObject {
        val multihash = link.multihash.toMultihash()

        return try {
            s3Client.getObject {
                it.bucket(bucketName)
                    .key(multihashToKeyMapper(link.type, link.multihash.toMultihash()))
            }
        } catch (e: NoSuchKeyException) {
            throw ObjectNotFoundException(link)
        } catch (e: InvalidObjectStateException) {
            throw ObjectNotReadyException(link)
        }.use { input ->
            // max support 2 GB
            val rawBytes = input.readBytes()
            // check loaded hash
            val loadedHash = when (multihash.type) {
                primaryMultihashProvider.getType() -> primaryMultihashProvider.digest(rawBytes)
                else -> throw UnsupportedOperationException(
                    "Unsupported multihash type: ${multihash.type}. " +
                            "Only ${primaryMultihashProvider.getType()} is supported"
                )
            }
            if (multihash != loadedHash) throw MultihashNotMatchException(multihash, loadedHash)
            AritegObject.parseFrom(rawBytes)
        }
    }

    override fun deleteProto(link: AritegLink): Boolean {
        if (!linkExists(link))
            throw ObjectNotFoundException(link)

        s3Client.deleteObject {
            it.bucket(bucketName)
                .key(multihashToKeyMapper(link.type, link.multihash.toMultihash()))
        }
        return true
    }

    override fun close() {
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
    }
}
