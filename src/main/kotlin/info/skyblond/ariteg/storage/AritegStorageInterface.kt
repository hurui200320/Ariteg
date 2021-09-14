package info.skyblond.ariteg.storage

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import java.io.InputStream

interface AritegStorageInterface : AutoCloseable {
    /**
     * Parse a Base58 multihash into a [AritegLink].
     *
     * Return null if storage cannot provide the link of this multihash.
     * */
    fun parse(multihashString: String): AritegLink?

    /**
     * Store a proto to the storage, return a link with a given name
     * */
    fun storeProto(name: String, proto: AritegObject): AritegLink

    /**
     * Store a proto to the storage, return a link with empty name
     * */
    fun storeProto(proto: AritegObject): AritegLink {
        return storeProto("", proto)
    }

    /**
     * Check if the target proto exists.
     *
     * Return true if the target exists.
     * */
    fun protoExists(link: AritegLink): Boolean

    /**
     * Check the given proto if is ready to load, if not, restore it (S3 archive).
     * Return true if target is ready to load.
     *
     * Throw [ObjectNotFoundException] if object not found
     * */
    @Throws(ObjectNotFoundException::class)
    fun protoAvailable(link: AritegLink): Boolean

    /**
     * Load a proto from a given link.
     * Throw [ObjectNotFoundException] if object not found.
     * Throw [ObjectNotReadyException] if object not ready to load.
     * */
    fun loadProto(link: AritegLink): AritegObject

    /**
     * Fetch: restore, load and return the proto.
     * Blocking caller until the proto is ready.
     * */
    fun fetchProtoBlocking(link: AritegLink): AritegObject

    /**
     * Save a inputStream to the storage and return the root object.
     * Will close the inputStream for you.
     *
     * If the total size smaller than blobSize, then BlobObject is returned.
     * Otherwise, a ListObject is returned.
     * */
    fun writeInputStreamToProto(
        inputStream: InputStream,
        blobSize: Int = 256 * 1024, // 256K
        listLength: Int = 176, // 176 blob in one list
    ): AritegObject

    /**
     * Return a inputStream representing a stream written by [writeInputStreamToProto].
     *
     * Only BlobObject and ListObject is allowed.
     * */
    fun readInputStreamFromProto(
        proto: AritegObject
    ): InputStream
}
