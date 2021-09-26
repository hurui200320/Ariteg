package info.skyblond.ariteg.storage.client.disk

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.multihash.MultihashProviders
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
import java.util.*
import java.util.concurrent.ConcurrentMap

/**
 * Native storage client is designed to work with native file system.
 * Normally native file system won't perform well on smaller files.
 *
 * This is only a proof of concept.
 * */
abstract class AbstractNativeStorageClient(
    private val baseDir: File,
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
    private val primaryMultihashProvider: MultihashProvider = MultihashProviders.sha3Provider512(),
    private val secondaryMultihashProvider: MultihashProvider = MultihashProviders.blake2b512Provider()
) : StorageClient {
    /**
     * Simple KV database stored in file.
     * */
    private val dbFile = File(baseDir, "client.db")

    // The transaction used in mapdb is not ACID transactions.
    // It just flushes data into disk so the data won't get corrupted.
    private val db = DBMaker.fileDB(dbFile)
        .fileMmapEnableIfSupported()
        .transactionEnable()
        .make()

    private val objectTypeMap: ConcurrentMap<ByteString, String> = db
        .hashMap("object_type_map", SerializerByteString(), Serializer.STRING)
        .createOrOpen()

    private val objectMultihashMap: ConcurrentMap<ByteString, ByteString> = db
        .hashMap("object_multihash_map", SerializerByteString(), SerializerByteString())
        .createOrOpen()

    private val objectWritingMap: ConcurrentMap<ByteString, ByteString> = db
        .hashMap("object_status_map", SerializerByteString(), SerializerByteString())
        .createOrOpen()

    /**
     * Get the file object based on ObjectType.
     *
     * Subclasses can have their own storage layout, like hash trie-ish folders.
     * */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun getObjectFile(type: String, multihashBase58: String): File {
        val parentDir = File(baseDir, type).also { it.mkdirs() }
        return File(parentDir, multihashBase58)
    }

    /**
     * Subclass need to implement this to actually write [rawBytes] into [file].
     * Before writing the data, run [preWrite], then write, then run [postWrite].
     *
     * Subclass can write data in the call, or put it into a queue and do it later.
     * */
    protected abstract fun handleWrite(
        file: File, rawBytes: ByteArray,
        preWrite: () -> Unit, postWrite: () -> Unit
    )

    override fun storeProto(name: String, proto: AritegObject): AritegLink {
        val rawBytes = proto.toByteArray()
        val primaryMultihash = primaryMultihashProvider.digest(rawBytes)
        val primaryMultihashByteString = ByteString.copyFrom(primaryMultihash.toBytes())
        val secondaryMultihash = secondaryMultihashProvider.digest(rawBytes)
        val secondaryMultihashByteString = ByteString.copyFrom(secondaryMultihash.toBytes())

        // not presenting in type db
        if (!objectTypeMap.containsKey(primaryMultihashByteString)) {
            // set version if no one is writing, get old version
            val oldSecondaryMultihash = objectWritingMap.putIfAbsent(
                primaryMultihashByteString, secondaryMultihashByteString
            )?.toMultihash()
            if (oldSecondaryMultihash != null) {
                // someone is writing, check the secondary hash
                require(oldSecondaryMultihash == secondaryMultihash) {
                    val encodedData = Base64.getEncoder().encode(rawBytes)
                    "Hash check failed! Data '${encodedData}' has the same primary hash with ${primaryMultihash.toBase58()}, " +
                            "but secondary hash is ${secondaryMultihash.toBase58()}, " +
                            "expected ${oldSecondaryMultihash.toBase58()}"
                }
                // The check will failed the process if secondary hash not match
            } else {
                // no one is writing, this client can write
                val type = proto.type.name.lowercase()
                val file = getObjectFile(type, primaryMultihash.toBase58())
                handleWrite(file, rawBytes, {}, {
                    synchronized(db) {
                        // add to type db
                        objectTypeMap[primaryMultihashByteString] = type
                        // add to secondary hash map
                        objectMultihashMap[primaryMultihashByteString] = secondaryMultihashByteString
                        // remove from writing queue
                        check(
                            objectWritingMap.remove(
                                primaryMultihashByteString,
                                secondaryMultihashByteString
                            )
                        ) { "Cannot remove entry from writing map. Data corrupt!" }
                        // flush changes to file
                        db.commit()
                    }
                })
            }
        }

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(primaryMultihashByteString)
            .build()
    }

    override fun deleteProto(proto: AritegObject): Boolean {
        val rawBytes = proto.toByteArray()
        val multihash = primaryMultihashProvider.digest(rawBytes)
        return deleteProto(multihash)
    }

    override fun deleteProto(link: AritegLink): Boolean {
        return deleteProto(link.multihash.toMultihash())
    }

    private fun deleteProto(multihash: Multihash): Boolean {
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())
        val type = synchronized(db) {
            // try to remove from type map
            val type = objectTypeMap.remove(multihashByteString)
            if (type != null) {
                // object exists, delete secondary hash and file
                objectMultihashMap.remove(multihashByteString)
                db.commit()
            }
            type
        }
        if (type != null) {
            // try to delete the file, ok to failed, it will be overwritten next time
            getObjectFile(type, multihash.toBase58()).delete()
        }
        // Not found, or in writing queue
        return false
    }

    override fun linkExists(link: AritegLink): Boolean {
        // object exists on disk
        if (objectTypeMap.contains(link.multihash))
            return true
        // object exists in memory
        if (objectWritingMap.contains(link.multihash))
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
        if (objectWritingMap.contains(link.multihash))
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
                primaryMultihashProvider.getType() -> primaryMultihashProvider.digest(rawBytes)
                else -> throw UnsupportedOperationException(
                    "Unsupported multihash type: ${multihash.type}. " +
                            "Only ${primaryMultihashProvider.getType()} is supported"
                )
            }
            if (multihash != loadedHash) throw HashNotMatchException(multihash, loadedHash)
            return AritegObject.parseFrom(rawBytes)
        }
        if (objectWritingMap.contains(link.multihash))
            throw ObjectNotReadyException(multihash)
        throw ObjectNotFoundException(multihash)
    }

    override fun close() {
        db.close()
    }
}
