package info.skyblond.ariteg.storage.client.disk

import com.google.protobuf.ByteString
import io.ipfs.multihash.Multihash
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * This storage client only block a small amount of time to
 * write metadata and return. The writing will be performed
 * asynchronously.
 * */
class AsyncNativeStorageClient(
    baseDir: File,
    threadNum: Int = Runtime.getRuntime().availableProcessors()
) : AbstractNativeStorageClient(baseDir) {
    private val threadPool = Executors.newFixedThreadPool(threadNum)

    override fun handleWrite(multihash: Multihash, type: String, file: File, rawBytes: ByteArray) {
        threadPool.execute {
            file.writeBytes(rawBytes)
            // add to type db
            val multihashByteString = ByteString.copyFrom(multihash.toBytes())
            objectTypeMap[multihashByteString] = type
            // remove from writing queue
            require(writingQueue.remove(multihashByteString, rawBytes.size)) { "Data corrupt!" }
        }
    }

    override fun close() {
        super.close()
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
    }

}
