package info.skyblond.ariteg.proto.meta.mapdb

import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.proto.meta.ProtoMetaService
import io.ipfs.multihash.Multihash
import org.mapdb.DBMaker
import java.io.File
import java.util.concurrent.ConcurrentMap

class MapDBProtoMetaService(
    dbFile: File
) : ProtoMetaService {
    // The transaction used in mapdb is not ACID transactions.
    // It just flushes data into disk so the data won't get corrupted.
    private val db = DBMaker.fileDB(dbFile)
        .fileMmapEnableIfSupported()
        .transactionEnable()
        .make()

    private val objectMultihashMap: ConcurrentMap<Multihash, ProtoMetaService.Entry> = db
        .hashMap("proto_entry_map", SerializerMultihash(), SerializerMetaEntry())
        .createOrOpen()

    override fun close() {
        db.close()
    }

    override fun getByPrimaryMultihash(primaryMultihash: Multihash): ProtoMetaService.Entry? =
        objectMultihashMap[primaryMultihash]

    override fun compareAndSetTempFlag(primaryMultihash: Multihash, oldValue: Long, newValue: Long?): Long? {
        val result = objectMultihashMap.computeIfPresent(primaryMultihash) { k, v ->
            assert(k == primaryMultihash)
            if (v.temp == oldValue)
                v.copy(temp = newValue)
            else
                v
        }
        db.commit()
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
        db.commit()
        // if we got null, then return the new created entry
        // else we return the old value (returned by putIfAbsent)
        return objectMultihashMap.putIfAbsent(
            primaryMultihash, entry
        ) ?: entry
    }

    override fun deleteByPrimaryMultihash(primaryMultihash: Multihash): ProtoMetaService.Entry? =
        objectMultihashMap.remove(primaryMultihash)
}
