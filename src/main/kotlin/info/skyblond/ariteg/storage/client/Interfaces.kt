package info.skyblond.ariteg.storage.client

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.storage.ObjectNotFoundException
import info.skyblond.ariteg.storage.ObjectNotReadyException
import io.ipfs.multihash.Multihash

/**
 * A storage client only handle the basic read and write of a proto object.
 * The client should maintain the underlying storage of proto data, and it's metadata,
 * there is no metadata required, but it's good to have depends on your implementation.
 * Something like the ObjectType of a given hash, the status of the proto, etc.
 *
 * This is a low level API facing proto and storage system. This interface should
 * be able to support native file system, AWS S3, and other storage system.
 * */
interface StorageClient : AutoCloseable {
    /**
     * Parse a Base58 multihash [String] into a [AritegLink].
     *
     * This is designed for clients that need perform additional check,
     * or some other actions when user querying a base58 multihash.
     *
     * Can throw exceptions something goes wrong. Also, it can return null
     * if the client want to ensure all links returned is valid.
     *
     * The default implementation just decode and feed the multihash into Ariteg link.
     * No further check is performed.
     * */
    fun parse(multihashString: String): AritegLink? {
        val bytestring = ByteString.copyFrom(Multihash.fromBase58(multihashString).toBytes())
        return AritegLink.newBuilder()
            .setMultihash(bytestring)
            .build()
    }

    /**
     * Store a proto to the storage, return a link with the given name.
     *
     * Store existing proto should have no bad side effects, since the proto
     * is immutable.
     * */
    fun storeProto(name: String, proto: AritegObject): AritegLink

    /**
     * Store a proto to the storage, return a link with empty name.
     *
     * @see storeProto
     * */
    fun storeProto(proto: AritegObject): AritegLink {
        return storeProto("", proto)
    }

    /**
     * Delete proto. *This will cause huge problem if you delete while writing.*
     *
     * Writing process wil likely reuse the proto on disk, if you delete that,
     * the client will assume it's on disk but actually not.
     *
     * Use on your own risk.
     * */
    fun deleteProto(proto: AritegObject): Boolean

    /**
     * Delete proto. *This will cause huge problem if you delete while writing.*
     *
     * Writing process wil likely reuse the proto on disk, if you delete that,
     * the client will assume it's on disk but actually not.
     *
     * Use on your own risk.
     * */
    fun deleteProto(link: AritegLink): Boolean

    /**
     * Check if the target proto exists. Aka known by the system.
     *
     * Return true if the target exists. In case the client cannot ensure
     * the link returned in [parse] is valid, user can check it using this
     * method.
     * */
    fun linkExists(link: AritegLink): Boolean

    /**
     * Prepare link for reading. Some storage system like AWS S3
     * need restore the object before reading (Glacier).
     *
     * The [option] depends. AWS S3 might require a duration for how long
     * the copy stay, and some other system might have different option.
     *
     * Might throw any exception if something goes wrong.
     * */
    fun prepareLink(link: AritegLink, option: PrepareOption)

    /**
     * Check the given proto if is ready to load.
     * Return true if target is ready to load.
     *
     * Throw [ObjectNotFoundException] if object not found.
     * Throw [ObjectNotReadyException] if the object is not prepared before. This
     * is designed for sanity check, in case you forget prepare it first.
     * */
    @Throws(ObjectNotFoundException::class, ObjectNotReadyException::class)
    fun linkAvailable(link: AritegLink, restore: Boolean = false): Boolean

    /**
     * Load a proto from a given link.
     * Throw [ObjectNotFoundException] if object not found.
     * Throw [ObjectNotReadyException] if object not ready to load.
     * */
    @Throws(ObjectNotFoundException::class, ObjectNotReadyException::class)
    fun loadProto(link: AritegLink): AritegObject
}

/**
 * Interface of prepare option. Different storage client may require different
 * class for option.
 * */
interface PrepareOption
