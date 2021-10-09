package info.skyblond.ariteg.storage.client

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
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
     * Delete proto. *This will cause huge problem if you delete while writing.*
     *
     * Writing process wil likely reuse the proto on disk, if you delete that,
     * the client will assume it's on disk but actually not.
     *
     * Use on your own risk.
     * */
    fun deleteProto(proto: AritegObject): Boolean


}


