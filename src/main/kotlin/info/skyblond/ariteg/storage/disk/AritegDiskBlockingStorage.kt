package info.skyblond.ariteg.storage.disk

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.storage.doSHA3Multihash512
import java.io.File

/**
 * This storage will block requests until write is finished.
 * */
class AritegDiskBlockingStorage(
    baseDir: File
) : AritegDiskAbstractStorage(baseDir) {
    override fun storeProto(name: String, proto: AritegObject): AritegLink {
        val rawBytes = proto.toByteArray()
        val multihash = doSHA3Multihash512(rawBytes)
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())

        if (!objectTypeMap.containsKey(multihashByteString)) {
            // not presenting in type db, query writing queue
            val oldSize = writingQueue.putIfAbsent(multihashByteString, rawBytes.size)
            if (oldSize != null) {
                // someone is writing
                require(oldSize == rawBytes.size) { "Hash collide!" }
            } else {
                // now writing
                val type = proto.type.name.lowercase()
                getObjectFile(type, multihash.toBase58()).writeBytes(rawBytes)
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
}
