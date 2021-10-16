package info.skyblond.ariteg.proto.storage

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import io.ipfs.multihash.Multihash
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * A proto storage service interface for different backend. It only handles basic
 * I/O requests. Like writing proto into disk/S3/something else, or read proto
 * from somewhere.
 * */
interface ProtoStorageService : AutoCloseable {
    /**
     * Store a proto to the storage, return a link with the given name.
     *
     * The [check] (primary hash, secondary hash) will be called before writing
     * the proto, return false will cancel this request. You can check and set
     * metadata here, and cancel the request if the proto has already exists.
     *
     * Storing existing proto should have no bad side effects, since the proto
     * is immutable.
     * */
    fun storeProto(
        name: String,
        proto: AritegObject,
        check: (Multihash, Multihash) -> Boolean,
    ): Pair<AritegLink, CompletableFuture<Multihash?>>

    /**
     * Check if the target proto exists on the backend.
     *
     * Return true if the target exists.
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
     * Throw [ObjectNotPreparedException] if the object is not prepared. This
     * is designed for sanity check, in case you forget prepare it first.
     * */
    @Throws(ObjectNotFoundException::class, ObjectNotPreparedException::class)
    fun linkAvailable(link: AritegLink): Boolean

    /**
     * Load a proto from a given link.
     * Throw [ObjectNotFoundException] if object not found.
     * Throw [ObjectNotReadyException] if object not ready to load.
     * */
    @Throws(ObjectNotFoundException::class, ObjectNotReadyException::class)
    fun loadProto(link: AritegLink): AritegObject

    /**
     * Delete proto. *This will cause huge problem if you delete proto while writing.*
     *
     * Writing process wil likely reuse the proto on disk, if you delete that,
     * the client will assume it's on disk but actually not.
     *
     * Use on your own risk.
     * */
    @Deprecated(
        "This operation is not recommended. Use on your own risk. " +
                "Copy your objects to a new backend instead of delete unused proto."
    )
    fun deleteProto(link: AritegLink): Boolean

    /**
     * Return the primary multihash type.
     * */
    fun getPrimaryMultihashType(): Multihash.Type

    /**
     * Return the secondary multihash type.
     * */
    fun getSecondaryMultihashType(): Multihash.Type

    /**
     * Interface of prepare option. Different storage client may require different
     * class for option.
     * */
    interface PrepareOption
}
