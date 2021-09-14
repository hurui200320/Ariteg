package info.skyblond.ariteg.storage.disk

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.AritegObjectType
import info.skyblond.ariteg.storage.*
import io.ipfs.multihash.Multihash
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

class AritegDiskStorage(
    private val baseDir: File
) : AritegAbstractStorage() {
    private val dbFile = File(baseDir, "objects.db")
    private val db = DBMaker.fileDB(dbFile)
        .fileMmapEnableIfSupported()
        .make()
    private val objectTypeMap: ConcurrentMap<ByteString, String> = db
        .hashMap("object_type_map", SerializerByteString(), Serializer.STRING)
        .createOrOpen()

    // The writing process is blocking, but read request might be multi thread
    private val writingQueue: ConcurrentMap<ByteString, Int> = ConcurrentHashMap()

    override fun parse(multihashString: String): AritegLink? {
        val bytestring = ByteString.copyFrom(Multihash.fromBase58(multihashString).toBytes())
        val value = objectTypeMap[bytestring] ?: return null
        val size = File(File(baseDir, value), multihashString).length()
        return AritegLink.newBuilder()
            .setMultihash(bytestring)
            .setSize(size)
            .build()
    }

    override fun storeProto(name: String, proto: AritegObject): AritegLink {
        val rawBytes = proto.toByteArray()
        val multihash = doSHA3Multihash512(rawBytes)
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())

        if (!objectTypeMap.containsKey(multihashByteString)) {
            // not presenting in type db, query writing queue first
            val oldSize = writingQueue.putIfAbsent(multihashByteString, rawBytes.size)
            if (oldSize != null) {
                // someone is writing
                require(oldSize == rawBytes.size) { "Hash collide!" }
            } else {
                // now writing
                val type = proto.typeOfObject.name.lowercase()
                val parentDir = File(baseDir, type)
                parentDir.mkdirs()
                File(parentDir, multihash.toBase58()).writeBytes(rawBytes)
                // add to type db
                objectTypeMap[multihashByteString] = type
                // remove from writing queue
                require(writingQueue.remove(multihashByteString, rawBytes.size)) { "Data corrupt!" }
            }
        }

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(multihashByteString)
            .setSize(rawBytes.size.toLong())
            .build()
    }

    override fun protoExists(link: AritegLink): Boolean {
        // ok to read objects on disk
        if (objectTypeMap.contains(link.multihash))
            return true
        // ok to read objects in writing queue
        if (writingQueue.contains(link.multihash))
            return true
        // not found
        return false
    }

    override fun protoAvailable(link: AritegLink): Boolean {
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
            val type = objectTypeMap[link.multihash] ?: throw IllegalStateException("Target not found")
            val file = File(File(baseDir, type), link.multihash.toMultihash().toBase58())
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
