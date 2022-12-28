package info.skyblond.ariteg.slicers.rolling

import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.storage.obj.Blob
import java.io.InputStream

abstract class RollingHashSlicer(
    private val targetFingerprint: UInt,
    private val fingerprintMask: UInt,
    private val minChunkSize: Int,
    private val maxChunkSize: Int,
    private val windowSize: Int,
    private val bufferSize: Int
) : Slicer {

    init {
        require(windowSize < minChunkSize) { "Window size must smaller than minChunkSize" }
    }

    /**
     * Calculate hash using the initial window.
     * Will be called before [calcNextHash]
     * */
    protected abstract fun initWindowHash(bytes: Array<UInt>): UInt

    /**
     * Calculate hash using currentHash, inByte and outByte.
     * */
    protected abstract fun calcNextHash(currentHash: UInt, inByte: UInt, outByte: UInt): UInt


    override fun slice(input: InputStream): Sequence<Blob> = sequence {
        // window buffer
        val windowBuffer = Array(windowSize) { 0u }
        var windowBufferPos = 0
        // chunk buffer
        val chunkBuffer = ByteArray(maxChunkSize)
        var chunkPos = 0
        // fill up the window
        ByteArray(windowSize).also { buffer ->
            val readCount = input.read(buffer)
            if (readCount != windowSize) {
                // file is too small, just return it as a chunk
                if (readCount != -1) {
                    yield(Blob(buffer.copyOfRange(0, readCount)))
                } // we're done
                return@sequence
            }
            for (i in windowBuffer.indices) {
                windowBuffer[i] = buffer[i].toUInt() and 0xFFu
                chunkBuffer[chunkPos++] = buffer[i]
            }
        }
        // calculate hash
        var hash: UInt = initWindowHash(windowBuffer)

        // input buffer
        val buffer = ByteArray(bufferSize)
        while (true) {
            // load buffer
            val readCount = input.read(buffer)
            if (readCount == -1) break // EOF
            for (i in 0 until readCount) {
                // for each new byte
                // check old data first
                if (// we have enough bytes and there is a chunk
                    (chunkPos >= minChunkSize && hash and fingerprintMask == targetFingerprint)
                    // we have too many bytes, we must chunk
                    || chunkPos >= maxChunkSize
                ) {
                    val chunk = chunkBuffer.copyOfRange(0, chunkPos)
                    chunkPos = 0
                    yield(Blob(chunk)) // generate new blob
                }
                // for new byte, update window
                val inByte = buffer[i].toUInt() and 0xFFu
                val outByte = windowBuffer[windowBufferPos]
                windowBuffer[windowBufferPos++] = inByte
                windowBufferPos %= windowSize
                hash = calcNextHash(hash, inByte, outByte)
                // save new byte to buffer
                chunkBuffer[chunkPos++] = buffer[i]
            }
        }
        // now we have read and processed all data
        // but there still might something left
        if (chunkBuffer.isNotEmpty()) {
            val chunk = chunkBuffer.copyOfRange(0, chunkPos)
            yield(Blob(chunk)) // generate last blob
        }
    }
}
