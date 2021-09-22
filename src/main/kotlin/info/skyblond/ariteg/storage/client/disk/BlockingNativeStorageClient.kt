package info.skyblond.ariteg.storage.client.disk

import com.google.protobuf.ByteString
import io.ipfs.multihash.Multihash
import java.io.File

/**
 * This storage client will block requests until the writing is finished.
 * */
class BlockingNativeStorageClient(
    baseDir: File
) : AbstractNativeStorageClient(baseDir) {
    override fun handleWrite(multihash: Multihash, type: String, file: File, rawBytes: ByteArray) {
        file.writeBytes(rawBytes)
        // add to type db
        val multihashByteString = ByteString.copyFrom(multihash.toBytes())
        objectTypeMap[multihashByteString] = type
        // remove from writing queue
        require(writingQueue.remove(multihashByteString, rawBytes.size)) { "Data corrupt!" }

    }
}
