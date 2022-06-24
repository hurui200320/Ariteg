package info.skyblond.ariteg.slicers

import info.skyblond.ariteg.Blob
import info.skyblond.ariteg.ConfigService.getCacheBlobDir
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * This class represent a cache on local disk.
 * Thus, no encryption or compress is applied when writing.
 */
data class BlobDescriptor(
    val hash: String,
    val file: File
) {
    fun readBlob(): CompletableFuture<Blob> {
        val content = FileUtils.readFileToByteArray(this.file)
        val blob = Blob(content)
        return blob.verify(this.hash)
            .thenApplyAsync {
                if (it) { // ok
                    blob
                } else { // failed to verify
                    throw IllegalStateException("Hash not match")
                }
            }
    }

    companion object : AutoCloseable {
        private val threadPool = Executors.newSingleThreadExecutor()

        init {
            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    this@Companion.close()
                }
            })
        }

        /**
         * Write blobs using single thread, to prevent racing on same hash
         * */
        fun writeBlob(blob: Blob): CompletableFuture<BlobDescriptor> = CompletableFuture.supplyAsync({
            val hash = blob.getHashString().get()
            val file = File(getCacheBlobDir(), hash)
            FileUtils.writeByteArrayToFile(file, blob.encodeToBytes())
            BlobDescriptor(hash, file)
        }, threadPool)

        override fun close() {
            threadPool.shutdown()
        }
    }
}
