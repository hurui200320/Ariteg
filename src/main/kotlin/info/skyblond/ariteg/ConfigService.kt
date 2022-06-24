package info.skyblond.ariteg

import java.io.File

object ConfigService {

    // Where to put cached blobs
    fun getCacheBlobDir(): File {
        // TODO
        return File("./data/blob-cache").also { it.mkdirs() }
    }

    // how much cached blobs are allowed during the slicing
//    fun getSlicingCachedBlobLimit(): Int {
//        // TODO
//        return 2048
//    }
}
