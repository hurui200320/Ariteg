package info.skyblond.ariteg.proto.meta

import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.multihash.MultihashSerializer
import io.ipfs.multihash.Multihash
import kotlinx.serialization.Serializable

/**
 * A proto meta service provides metadata management for protos.
 * Like secondary multihash check before write.
 *
 * This interface only require atomic for those request, most of the databases
 * should be able to satisfied that.
 * */
interface ProtoMetaService : AutoCloseable {

    @Serializable
    data class Entry(
        @Serializable(with = MultihashSerializer::class)
        val primaryMultihash: Multihash,
        @Serializable(with = MultihashSerializer::class)
        val secondaryMultihash: Multihash,
        val type: ObjectType,
        val temp: Long?
    )

    /**
     * Get proto's meta by primary hash. Return null if not found.
     * */
    fun getByPrimaryMultihash(primaryMultihash: Multihash): Entry?

    /**
     * Compare and set temp flag. Return new value if success, return current
     * value if failed.
     * */
    fun compareAndSetTempFlag(primaryMultihash: Multihash, oldValue: Long, newValue: Long?): Long?

    /**
     * Save new entry if the primary hash is not exists. Should be atomic.
     * Return new created entry, or return already exists one.
     * */
    fun saveIfPrimaryMultihashNotExists(
        primaryMultihash: Multihash,
        secondaryMultihash: Multihash,
        type: ObjectType,
        temp: Long
    ): Entry

    /**
     * Delete proto's meta and return the deleted one. Return null if not found.
     * *Not recommended. Use on your own risk.*
     * */
    @Deprecated(
        "This operation is not recommended. Use on your own risk. " +
                "Copy your objects to a new backend instead of delete unused proto."
    )
    fun deleteByPrimaryMultihash(primaryMultihash: Multihash): Entry?
}
