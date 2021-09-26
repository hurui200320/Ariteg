package info.skyblond.ariteg.storage.client.disk

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
    private val counter = AtomicLong(0)

    override fun handleWrite(file: File, rawBytes: ByteArray, preWrite: () -> Unit, postWrite: () -> Unit) {
        counter.incrementAndGet()
        threadPool.execute {
            preWrite.invoke()
            file.writeBytes(rawBytes)
            postWrite.invoke()
            counter.decrementAndGet()
        }
    }

    /**
     * return if all writing job are done
     * */
    fun allClear(): Boolean {
        return counter.get() == 0L
    }

    override fun close() {
        super.close()
        threadPool.shutdown()
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
    }

}
