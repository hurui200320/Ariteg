package info.skyblond.ariteg

import info.skyblond.ariteg.multihash.MultihashJavaProvider
import info.skyblond.ariteg.storage.client.disk.AsyncNativeStorageClient
import info.skyblond.ariteg.storage.layer.FileNativeStorageLayer
import info.skyblond.ariteg.storage.layer.StorageLayer
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.min
import kotlin.random.Random


object Main {
    private val logger = LoggerFactory.getLogger("Application")

    private fun makeTestFile(file: File, size: Long) {
        println("Creating test file: $size byte(s)")
        val buffer = ByteArray(4096) // 4KB
        file.outputStream().use { outputStream ->
            var counter = 0L
            while (counter < size) {
                Random.nextBytes(buffer)
                outputStream.write(buffer, 0, min(buffer.size.toLong(), size - counter).toInt())
                counter += buffer.size
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // create test dir
        // A test dir contains:
        //  + empty dir
        //  + non-empty dir
        //      + empty file (empty blob)
        //      + small file (single blob)
        //      + middle file (a list with some blob)
        //      + big file (a list with some list and blob)
        //      + large file (a list with sub list)
        val baseDir = File("./test_source_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
            .also { it.mkdirs() }
        File(baseDir, "empty dir").also { it.mkdirs() }
        val dir = File(baseDir, "normal dir").also { it.mkdirs() }
        for (size in listOf(
            0L, // empty file
            StorageLayer.DEFAULT_BLOB_SIZE - 1L, // small file
            2L * StorageLayer.DEFAULT_BLOB_SIZE, // middle file
            (StorageLayer.DEFAULT_LIST_LENGTH + 1L) * StorageLayer.DEFAULT_BLOB_SIZE, // big file
            2L * StorageLayer.DEFAULT_LIST_LENGTH * StorageLayer.DEFAULT_BLOB_SIZE, // large file
        )) {
            makeTestFile(File(dir, "${System.currentTimeMillis()}_${System.nanoTime()}"), size)
        }

        val dataDir = File("./test_data_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
            .also { it.mkdirs() }

        val sha3Provider512 = MultihashJavaProvider.createSha3provider512()
        AsyncNativeStorageClient(dataDir).use { storageClient ->
            val storageLayer = FileNativeStorageLayer(storageClient)

            val obj = storageLayer.storeDir(baseDir)
            println("Stored finished!")
            storageLayer.walkTree(obj) { _, layer, path, proto ->
                println(path)
                val targetFile = File(baseDir, path.removePrefix("/"))
                val loadedHash = layer.readInputStreamFromProto(proto).use { sha3Provider512.digest(it, 4096 * 1024) }
                println(loadedHash.toBase58())
                println(targetFile)
                val targetHash = targetFile.inputStream().use { sha3Provider512.digest(it, 4096 * 1024) }
                println(targetHash.toBase58())
                require(loadedHash == targetHash)
                println()
            }
            logger.info("Done!")
        }

        baseDir.deleteRecursively()
        dataDir.deleteRecursively()
    }
}
