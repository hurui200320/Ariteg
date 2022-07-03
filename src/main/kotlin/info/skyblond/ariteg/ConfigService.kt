package info.skyblond.ariteg

import java.io.File
import java.util.concurrent.Semaphore

object ConfigService {

    // Where to put cached blobs
    fun getCacheBlobDir(): File {
        // TODO
        return File("./data/blob-cache").also { it.mkdirs() }
    }

    val uploadSemaphore = Semaphore(getUploadParallelLimit())

    // how much cached blobs are allowed during the slicing
    fun getUploadParallelLimit(): Int {
        // TODO
        return 16
    }


    val preloadSemaphore = Semaphore(getPreloadParallelLimit())
    fun getPreloadParallelLimit(): Int {
        // TODO
        return 1024
    }

}
