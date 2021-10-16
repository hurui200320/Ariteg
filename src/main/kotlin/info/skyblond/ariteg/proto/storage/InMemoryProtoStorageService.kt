package info.skyblond.ariteg.proto.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.objects.toMultihash
import io.ipfs.multihash.Multihash
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * This is a standard implementation reference for [ProtoStorageService].
 * It uses [ConcurrentHashMap] to prevent corruption when clients writing to same
 * file/key/whatever you call it.
 * Thus, it can be used as a reference when implementing other services.
 *
 * Since it stores all data in memory, it is only a prototype.
 *
 * @see [info.skyblond.ariteg.proto.meta.InMemoryProtoMetaService]
 * */
class InMemoryProtoStorageService(
    private val primaryMultihashProvider: MultihashProvider,
    private val secondaryMultihashProvider: MultihashProvider,
) : ProtoStorageService {
    private val storage = ConcurrentHashMap<Multihash, AritegObject>()

    private val writeCounter = AtomicLong(0)

    fun getWriteCount(): Long = writeCounter.get()

    override fun getPrimaryMultihashType(): Multihash.Type = primaryMultihashProvider.getType()

    override fun getSecondaryMultihashType(): Multihash.Type = secondaryMultihashProvider.getType()

    override fun close() = Unit

    override fun storeProto(
        name: String,
        proto: AritegObject,
        check: (Multihash, Multihash) -> Boolean
    ): Pair<AritegLink, CompletableFuture<Multihash?>> {
        // get raw bytes
        val rawBytes = proto.toByteArray()
        // calculate multihash
        val primaryMultihash = primaryMultihashProvider.digest(rawBytes)

        val future = CompletableFuture.supplyAsync {
            // calculate secondary hash
            val secondaryMultihash = secondaryMultihashProvider.digest(rawBytes)
            // run the check, return if we get false
            if (check(primaryMultihash, secondaryMultihash)) {
                writeCounter.incrementAndGet()
                storage[primaryMultihash] = proto
                return@supplyAsync primaryMultihash
            }
            return@supplyAsync null
        }

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(ByteString.copyFrom(primaryMultihash.toBytes()))
            .setType(proto.type)
            .build() to future
    }

    override fun linkExists(link: AritegLink): Boolean = storage[link.multihash.toMultihash()] != null

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
        val result = storage[multihash] ?: throw ObjectNotFoundException(link)

        val rawBytes = result.toByteArray()
        // check loaded hash
        val loadedHash = when (multihash.type) {
            primaryMultihashProvider.getType() -> primaryMultihashProvider.digest(rawBytes)
            else -> throw UnsupportedOperationException(
                "Unsupported multihash type: ${multihash.type}. " +
                        "Only ${primaryMultihashProvider.getType()} is supported"
            )
        }
        if (multihash != loadedHash) throw MultihashNotMatchException(multihash, loadedHash)
        return result
    }

    override fun deleteProto(link: AritegLink): Boolean {
        return storage.remove(link.multihash.toMultihash()) != null
    }
}
