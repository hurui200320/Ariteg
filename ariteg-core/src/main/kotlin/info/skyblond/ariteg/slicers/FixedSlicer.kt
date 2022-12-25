package info.skyblond.ariteg.slicers

import info.skyblond.ariteg.Blob
import java.io.InputStream

/**
 * Slice file into fixed length chunks.
 * This is thread-safe. But the sequence is not.
 * */
class FixedSlicer(
    private val chunkSizeInByte: Int
) : Slicer {
    override fun slice(input: InputStream): Sequence<Blob> = sequence {
        val buffer = ByteArray(chunkSizeInByte)
        while (true) {
            val readCount = input.read(buffer)
            if (readCount == -1) break // EOF
            yield(Blob(buffer.copyOfRange(0, readCount)))
        }
    }
}
