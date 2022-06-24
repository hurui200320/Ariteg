package info.skyblond.ariteg.slicers.rolling

import info.skyblond.ariteg.Blob
import info.skyblond.ariteg.slicers.BlobDescriptor
import info.skyblond.ariteg.slicers.Slicer
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

abstract class RollingHashSlicer(
    private val file: File,
    protected val targetFingerprint: UInt,
    protected val fingerprintMask: UInt,
    private val minChunkSize: Int,
    private val maxChunkSize: Int,
    private val windowSize: Int,
    private val channelBufferSize: Int = 32 * 1024 * 1024
) : Slicer {

    init {
        require(windowSize < minChunkSize) { "Window size must smaller than minChunkSize" }
        require(file.length() >= windowSize) { "File size must bigger or equal than window size" }
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


    override fun iterator(): Iterator<CompletableFuture<BlobDescriptor>> =
        object : Iterator<CompletableFuture<BlobDescriptor>>, AutoCloseable {
            // for calculating split point
            private val randomAccessFile = RandomAccessFile(file, "r")
            private val fileChannel = randomAccessFile.channel
            private val channelBuffer = ByteBuffer.allocate(channelBufferSize)
            private var channelBufferMax: Int = 0
            private var channelBufferPos: Int = 0

            private val windowBuffer = Array(windowSize) { readNextWindowByte()!! }
            private var windowBufferPos = 0

            private var hash: UInt = initWindowHash(windowBuffer)

            // for making chunks
            private val fileStream = FileInputStream(file)

            private fun isFingerprintMatch(): Boolean {
                return this.hash and fingerprintMask == targetFingerprint
            }

            private fun readNextWindowByte(): UInt? {
                if (this.channelBufferPos >= this.channelBufferMax) {
                    // read new data
                    this.channelBufferMax = this.fileChannel.read(this.channelBuffer)
                    this.channelBuffer.flip()
                    this.channelBufferPos = 0
                }
                // after reload, the pos==max
                // means this is really the end
                if (channelBufferPos == channelBufferMax) {
                    return null
                }
                this.channelBufferPos++
                return this.channelBuffer.get().toUInt() and 0xFFu
            }

            private var closed = false
            private var chunkSize = windowSize
            private val chunkBuffer = ByteArray(maxChunkSize)

            override fun hasNext(): Boolean {
                // try to find new chunks
                // Note: chunkSize will be windowSize at be beginning
                // Then will be zero at each call.
                // And no matter what, the chunkSize must bigger than minChunkSize
                // if not, then it's invalid, and we can find new chunks.
                if (chunkSize < minChunkSize && !closed) {
                    // find new chunk
                    while (true) {
                        if (chunkSize >= minChunkSize) {
                            // min chunk size reached, see if we can make a new chunk
                            if (isFingerprintMatch()) {
                                // fingerprint match, has next
                                fileStream.readNBytes(chunkBuffer, 0, chunkSize)
                                break
                            }
                        }
                        // update window
                        val inByte = readNextWindowByte()
                        if (inByte == null) {
                            // no bytes to read, current is the last chunk
                            if (chunkSize > 0) {
                                fileStream.readNBytes(chunkBuffer, 0, chunkSize)
                            }
                            closed = true
                            close()
                            break
                        }
                        val outByte = windowBuffer[windowBufferPos]
                        windowBuffer[windowBufferPos++] = inByte
                        windowBufferPos %= windowSize
                        hash = calcNextHash(hash, inByte, outByte)
                        chunkSize++

                        if (chunkSize >= maxChunkSize) {
                            // max chunk size reached, we have to make a new chunk
                            fileStream.readNBytes(chunkBuffer, 0, chunkSize)
                            break
                        }
                    }
                }
                // if chunkSize > 0, there has next chunk
                return chunkSize > 0
            }

            override fun next(): CompletableFuture<BlobDescriptor> {
                if (hasNext()) {
                    val chunk = ByteArray(chunkSize)
                    System.arraycopy(chunkBuffer, 0, chunk, 0, chunkSize)
                    // cleat last read count, so hasNext() can fill the buffer
                    chunkSize = 0
                    return BlobDescriptor.writeBlob(Blob(chunk))
                } else {
                    throw NoSuchElementException("No further elements")
                }
            }

            override fun close() {
                fileChannel.close()
                randomAccessFile.close()
                fileStream.close()
            }

        }
}
