package info.skyblond.ariteg.storage.disk

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.storage.*
import io.ipfs.multihash.Multihash
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class AritegDiskAbstractStorage(
    private val baseDir: File
) : AritegAbstractStorage() {
    private val dbFile = File(baseDir, "objects.db")
    private val db = DBMaker.fileDB(dbFile)
        .fileMmapEnableIfSupported()
        .make()
    protected val objectTypeMap: ConcurrentMap<ByteString, String> = db
        .hashMap("object_type_map", SerializerByteString(), Serializer.STRING)
        .createOrOpen()
    protected val writingQueue: ConcurrentMap<ByteString, Int> = ConcurrentHashMap()

    protected fun getObjectFile(type: String, multihashBase58: String): File {
        val parentDir = File(baseDir, type).also { it.mkdirs() }
        return File(parentDir, multihashBase58)
    }

    override fun parse(multihashString: String): AritegLink? {
        val bytestring = ByteString.copyFrom(Multihash.fromBase58(multihashString).toBytes())
        val value = objectTypeMap[bytestring] ?: return null
        val size = getObjectFile(value, multihashString).length()
        return AritegLink.newBuilder()
            .setMultihash(bytestring)
            .setSize(size)
            .build()
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

    override fun linkAvailable(link: AritegLink): Boolean {
        // read objects on disk
        if (objectTypeMap.contains(link.multihash))
            return true
        // read objects in writing queue
        if (writingQueue.contains(link.multihash))
            return false
        // not found
        throw ObjectNotFoundException(link.multihash.toMultihash().toBase58())
    }

    @Throws
    override fun loadProto(link: AritegLink): AritegObject {
        val multihash = link.multihash.toMultihash()
        if (objectTypeMap.contains(link.multihash)) {
            val type =
                objectTypeMap[link.multihash] ?: throw IllegalStateException("Map contains the key but value is null")
            val file = getObjectFile(type, link.multihash.toMultihash().toBase58())
            val rawBytes = file.readBytes()
            require(link.size == rawBytes.size.toLong()) { "Size not match. Expected: ${link.size}, actual: ${rawBytes.size}" }
            // check loaded hash
            val loadedHash = when (multihash.type) {
                Multihash.Type.sha3_512 -> doSHA3Multihash512(rawBytes)
                else -> throw UnsupportedOperationException("Unsupported multihash type: ${multihash.type}")
            }
            if (multihash != loadedHash) throw IncorrectHashException("Hash not match: expected: ${multihash.toBase58()}, actual: ${loadedHash.toBase58()}")
            return AritegObject.parseFrom(rawBytes)
        }
        if (writingQueue.contains(link.multihash))
            throw ObjectNotReadyException(multihash.toBase58())

        throw ObjectNotFoundException(multihash.toBase58())
    }

    override fun close() {
        db.close()
    }
}
