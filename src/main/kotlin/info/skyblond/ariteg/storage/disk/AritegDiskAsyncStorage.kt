package info.skyblond.ariteg.storage.disk

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.storage.doSHA3Multihash512
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * This storage only block a small amount of time to
 * write metadata and return. The writing will be performed
 * asynchronously.
 * */
class AritegDiskAsyncStorage(
    baseDir: File,
    threadNum: Int = Runtime.getRuntime().availableProcessors()
) : AritegDiskAbstractStorage(baseDir) {
    private val threadPool = Executors.newFixedThreadPool(threadNum)

    override fun storeProto(name: String, proto: AritegObject): AritegLink {
        val rawBytes = proto.toByteArray()
        val multihash = doSHA3Multihash512(rawBytes)
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())

        if (!objectTypeMap.containsKey(multihashByteString)) {
            // not presenting in type db, query writing queue
            val oldSize = writingQueue.putIfAbsent(multihashByteString, rawBytes.size)
            if (oldSize != null) {
                // someone is writing, check if the same
                require(oldSize == rawBytes.size) { "Hash collide!" }
            } else {
                // writing in future
                threadPool.execute {
                    val type = proto.type.name.lowercase()
                    getObjectFile(type, multihash.toBase58()).writeBytes(rawBytes)
                    // add to type db
                    objectTypeMap[multihashByteString] = type
                    // remove from writing queue
                    require(writingQueue.remove(multihashByteString, rawBytes.size)) { "Data corrupt!" }
                }
            }
        }

        return AritegLink.newBuilder()
            .setName(name)
            .setMultihash(multihashByteString)
            .setSize(rawBytes.size.toLong())
            .build()
    }

    override fun close() {
        super.close()
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
    }

}
