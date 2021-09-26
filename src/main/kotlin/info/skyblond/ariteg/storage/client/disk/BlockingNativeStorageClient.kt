package info.skyblond.ariteg.storage.client.disk

import java.io.File

/**
 * This storage client will block requests until the writing is finished.
 * */
class BlockingNativeStorageClient(
    baseDir: File
) : AbstractNativeStorageClient(baseDir) {

    override fun handleWrite(file: File, rawBytes: ByteArray, preWrite: () -> Unit, postWrite: () -> Unit) {
        preWrite.invoke()
        file.writeBytes(rawBytes)
        postWrite.invoke()
    }
}
