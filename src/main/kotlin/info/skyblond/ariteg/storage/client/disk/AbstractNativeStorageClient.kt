package info.skyblond.ariteg.storage.client.disk

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.multihash.MultihashJavaProvider
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.storage.HashNotMatchException
import info.skyblond.ariteg.storage.ObjectNotFoundException
import info.skyblond.ariteg.storage.ObjectNotReadyException
import info.skyblond.ariteg.storage.client.PrepareOption
import info.skyblond.ariteg.storage.client.StorageClient
import info.skyblond.ariteg.storage.toMultihash
import io.ipfs.multihash.Multihash
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Native storage client is designed to work with native file system.
 * Normally native file system won't perform well on smaller files.
 *
 * This is only a proof of concept.
 * */
abstract class AbstractNativeStorageClient(
    private val baseDir: File,
    private val multihashProvider: MultihashProvider = MultihashJavaProvider.createSha3provider512()
) : StorageClient {
    /**
     * Simple KV database stored in file.
     * */
    private val dbFile = File(baseDir, "client.db")

    private val db = DBMaker.fileDB(dbFile)
        .fileMmapEnableIfSupported()
        .make()

    protected val objectTypeMap: ConcurrentMap<ByteString, String> = db
        .hashMap("object_type_map", SerializerByteString(), Serializer.STRING)
        .createOrOpen()

    protected val writingQueue: ConcurrentMap<ByteString, Int> = ConcurrentHashMap()

    /**
     * Get the file object based on ObjectType
     * */
    private fun getObjectFile(type: String, multihashBase58: String): File {
        val parentDir = File(baseDir, type).also { it.mkdirs() }
        return File(parentDir, multihashBase58)
    }

    /**
     * This method is called when client actually need to write into disk.
     * When calling this method, the writing queue is set.
     * */
    protected abstract fun handleWrite(multihash: Multihash, type: String, file: File, rawBytes: ByteArray)

    override fun storeProto(name: String, proto: AritegObject): AritegLink {
        val rawBytes = proto.toByteArray()
        val multihash = multihashProvider.digest(rawBytes)
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())

        if (!objectTypeMap.containsKey(multihashByteString)) {
            // not presenting in type db, query writing queue
            val oldSize = writingQueue.putIfAbsent(multihashByteString, rawBytes.size)
            if (oldSize != null) {
                // someone is writing, check if the same
                require(oldSize == rawBytes.size) { "Hash collide!" }
            } else {
                // writing
                val type = proto.type.name.lowercase()
                val file = getObjectFile(type, multihash.toBase58())
                handleWrite(multihash, type, file, rawBytes)
            }
        }

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(multihashByteString)
            .build()
    }

    override fun deleteProto(proto: AritegObject): Boolean {
        val rawBytes = proto.toByteArray()
        val multihash = multihashProvider.digest(rawBytes)
        return deleteProto(multihash)
    }

    override fun deleteProto(link: AritegLink): Boolean {
        return deleteProto(link.multihash.toMultihash())
    }

    private fun deleteProto(multihash: Multihash): Boolean {
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())
        // try to remove from type map
        val type = objectTypeMap.remove(multihashByteString)
        if (type != null) {
            // object exists on disk
            return getObjectFile(type, multihash.toBase58()).delete()
        }
        // Cannot remove those in the writing queue
        return false
    }

    override fun linkExists(link: AritegLink): Boolean {
        // object exists on disk
        if (objectTypeMap.contains(link.multihash))
            return true
        // object exists in memory
        if (writingQueue.contains(link.multihash))
            return true
        // not found
        return false
    }

    override fun prepareLink(link: AritegLink, option: PrepareOption) {
        if (!linkExists(link))
            throw ObjectNotFoundException(link)
    }

    override fun linkAvailable(link: AritegLink, restore: Boolean): Boolean {
        // read objects on disk
        if (objectTypeMap.contains(link.multihash))
            return true
        // read objects in writing queue
        if (writingQueue.contains(link.multihash))
            return false
        // not found
        throw ObjectNotFoundException(link)
    }

    @Throws
    override fun loadProto(link: AritegLink): AritegObject {
        val multihash = link.multihash.toMultihash()
        val type = objectTypeMap[link.multihash]
        if (type != null) {
            val file = getObjectFile(type, link.multihash.toMultihash().toBase58())
            val rawBytes = file.readBytes()
            // check loaded hash
            val loadedHash = when (multihash.type) {
                multihashProvider.getType() -> multihashProvider.digest(rawBytes)
                else -> throw UnsupportedOperationException("Unsupported multihash type: ${multihash.type}. Only ${multihashProvider.getType()} is supported")
            }
            if (multihash != loadedHash) throw HashNotMatchException(multihash, loadedHash)
            return AritegObject.parseFrom(rawBytes)
        }
        if (writingQueue.contains(link.multihash))
            throw ObjectNotReadyException(multihash)
        throw ObjectNotFoundException(multihash)
    }

    override fun close() {
        db.close()
    }
}
