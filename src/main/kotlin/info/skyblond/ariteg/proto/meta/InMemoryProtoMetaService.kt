package info.skyblond.ariteg.proto.meta

import info.skyblond.ariteg.ObjectType
import io.ipfs.multihash.Multihash
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a standard implementation reference for [ProtoMetaService].
 * It uses [ConcurrentHashMap]'s atomic to implement the methods.
 * Thus, it can be used as a reference when implementing other services.
 *
 * Since it stores all data in memory, it is only a prototype.
 *
 * @see [info.skyblond.ariteg.proto.storage.InMemoryProtoStorageService]
 * */
class InMemoryProtoMetaService : ProtoMetaService {
    private val concurrentHashMap = ConcurrentHashMap<Multihash, ProtoMetaService.Entry>()

    override fun getByPrimaryMultihash(primaryMultihash: Multihash): ProtoMetaService.Entry? =
        concurrentHashMap[primaryMultihash]

    override fun compareAndSetTempFlag(primaryMultihash: Multihash, oldValue: Long, newValue: Long?): Long? {
        val result = concurrentHashMap.computeIfPresent(primaryMultihash) { k, v ->
            assert(k == primaryMultihash)
            if (v.temp == oldValue)
                v.copy(temp = newValue)
            else
                v
        }
        // result will be:
        //      newValue if we replace successfully
        //      oldValue if we failed to replace
        //      null     if the key not found
        return result?.temp
    }

    override fun saveIfPrimaryMultihashNotExists(
        primaryMultihash: Multihash,
        secondaryMultihash: Multihash,
        type: ObjectType,
        temp: Long
    ): ProtoMetaService.Entry {
        val entry = ProtoMetaService.Entry(
            primaryMultihash, secondaryMultihash, type, temp
        )
        // if we got null, then return the new created entry
        // else we return the old value (returned by putIfAbsent)
        return concurrentHashMap.putIfAbsent(
            primaryMultihash, entry
        ) ?: entry
    }

    override fun deleteByPrimaryMultihash(primaryMultihash: Multihash): ProtoMetaService.Entry? =
        concurrentHashMap.remove(primaryMultihash)

    override fun close() = Unit
}
