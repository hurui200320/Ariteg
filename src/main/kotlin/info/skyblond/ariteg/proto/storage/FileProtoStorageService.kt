package info.skyblond.ariteg.proto.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.objects.toMultihash
import io.ipfs.multihash.Multihash
import java.io.File
import java.util.concurrent.*

class FileProtoStorageService(
    private val baseDir: File,
    threadNum: Int,
    /**
     * Two different hash provider is required for detecting
     * hash collision. This is not fully eliminate the chance of
     * hash collision, but should work in almost all time.
     *
     * The primary provider should use a secure hash as possible as it can.
     * The secondary provider should be fast, like blake2b or blake3. It is calculated
     * to check if the content is the same. If the secondary hash gives different
     * result, then primary hash collision is detected, an error will be thrown,
     * the write request will be rejected.
     * */
    private val primaryMultihashProvider: MultihashProvider,
    private val secondaryMultihashProvider: MultihashProvider,
    queueSize: Int = 64,
    // baseDir, type, primary hash
    private val multihashToFileMapper: (File, ObjectType, Multihash) -> File = { b, t, k ->
        File(File(b, t.name.lowercase()).also { it.mkdirs() }, k.toBase58())
    }
) : ProtoStorageService {
    private val queue = LinkedBlockingQueue<Runnable>(queueSize)
    private val threadPool = ThreadPoolExecutor(
        threadNum, threadNum,
        0L, TimeUnit.MILLISECONDS,
        queue, ThreadPoolExecutor.CallerRunsPolicy()
    )

    override fun getPrimaryMultihashType(): Multihash.Type = primaryMultihashProvider.getType()

    override fun getSecondaryMultihashType(): Multihash.Type = secondaryMultihashProvider.getType()

    override fun storeProto(
        name: String,
        proto: AritegObject,
        check: (Multihash, Multihash) -> Boolean,
    ): Pair<AritegLink, CompletableFuture<Multihash?>> {
        // get raw bytes
        val rawBytes = proto.toByteArray()
        // calculate multihash
        val primaryMultihash = primaryMultihashProvider.digest(rawBytes)

        val future = CompletableFuture.supplyAsync({
            // calculate secondary hash
            val secondaryMultihash = secondaryMultihashProvider.digest(rawBytes)
            // run the check, return if we get false
            if (check(primaryMultihash, secondaryMultihash)) {
                // check pass, add request into queue
                val file = multihashToFileMapper(baseDir, proto.type, primaryMultihash)
                file.writeBytes(rawBytes)
                primaryMultihash
            } else {
                null
            }
        }, threadPool)

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(ByteString.copyFrom(primaryMultihash.toBytes()))
            .setType(proto.type)
            .build() to future
    }


    override fun linkExists(link: AritegLink): Boolean =
        multihashToFileMapper(baseDir, link.type, link.multihash.toMultihash()).exists()

    override fun prepareLink(link: AritegLink, option: ProtoStorageService.PrepareOption) {
        if (!linkExists(link))
            throw ObjectNotFoundException(link)
    }

    override fun linkAvailable(link: AritegLink): Boolean {
        if (linkExists(link))
            return true
        else
            throw ObjectNotFoundException(link)
    }

    override fun loadProto(link: AritegLink): AritegObject {
        val multihash = link.multihash.toMultihash()
        val file = multihashToFileMapper(baseDir, link.type, multihash)
        if (!file.exists())
            throw ObjectNotFoundException(link)
        val rawBytes = file.readBytes()
        // check loaded hash
        val loadedHash = when (multihash.type) {
            primaryMultihashProvider.getType() -> primaryMultihashProvider.digest(rawBytes)
            else -> throw UnsupportedOperationException(
                "Unsupported multihash type: ${multihash.type}. " +
                        "Only ${primaryMultihashProvider.getType()} is supported"
            )
        }
        if (multihash != loadedHash) throw MultihashNotMatchException(multihash, loadedHash)
        return AritegObject.parseFrom(rawBytes)
    }

    override fun deleteProto(link: AritegLink): Boolean {
        if (!linkExists(link))
            throw ObjectNotFoundException(link)
        return multihashToFileMapper(baseDir, link.type, link.multihash.toMultihash()).delete()
    }

    override fun close() {
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
    }
}
