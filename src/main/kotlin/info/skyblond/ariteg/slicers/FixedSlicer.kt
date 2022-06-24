package info.skyblond.ariteg.slicers

import info.skyblond.ariteg.Blob
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CompletableFuture

/**
 * Slice file into fixed length chunks.
 * This is thread-safe. But the iterator is NOT thread-safe.
 * */
class FixedSlicer(
    private val file: File,
    private val chunkSizeInByte: Int
) : Slicer {

    /**
     * Create a new iterator which will slice and return the blobs.
     * The returned iterator is not thread-safe.
     * */
    override fun iterator(): Iterator<CompletableFuture<BlobDescriptor>> =
        object : Iterator<CompletableFuture<BlobDescriptor>>, AutoCloseable {
            private val chunkStream = FileInputStream(file)
            private var closed = false
            private val chunkSize = chunkSizeInByte
            private val buffer = ByteArray(chunkSize)
            private var lastReadCount = 0

            // if not closed, has next
            override fun hasNext(): Boolean {
                // try read, but not read multiple times before next() is called
                if (lastReadCount == 0 && !closed) {
                    lastReadCount = chunkStream.readNBytes(buffer, 0, chunkSize)
                    if (lastReadCount == 0) {
                        closed = true
                        close()
                    }
                }
                // if not closed, then has next element
                return !closed
            }

            override fun next(): CompletableFuture<BlobDescriptor> {
                if (hasNext()) {
                    val chunk = ByteArray(lastReadCount)
                    System.arraycopy(buffer, 0, chunk, 0, lastReadCount)
                    // cleat last read count, so hasNext() can fill the buffer
                    lastReadCount = 0
                    return BlobDescriptor.writeBlob(Blob(chunk))
                } else {
                    throw NoSuchElementException("No further elements")
                }
            }

            override fun close() {
                chunkStream.close()
            }
        }

}
