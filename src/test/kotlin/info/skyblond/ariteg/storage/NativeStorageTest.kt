package info.skyblond.ariteg.storage

import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.multihash.MultihashJavaProvider
import info.skyblond.ariteg.storage.client.disk.AsyncNativeStorageClient
import info.skyblond.ariteg.storage.client.disk.BlockingNativeStorageClient
import info.skyblond.ariteg.storage.layer.FileNativeStorageLayer
import info.skyblond.ariteg.storage.layer.StorageLayer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.min
import kotlin.random.Random

class NativeStorageTest {
    private val logger = LoggerFactory.getLogger(NativeStorageTest::class.java)

    // Each test has it own instance, it's safe to use field
    private val sourceDir = File("./test_source_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
    private val dataDir = File("./test_data_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
    private val sha3Provider512 = MultihashJavaProvider.createSha3provider512()

    /**
     * A test dir contains:
     *  + empty dir
     *  + non-empty dir
     *      + empty file (empty blob)
     *      + small file (single blob)
     *      + middle file (a list with some blob)
     *      + big file (a list with some list and blob)
     *      + large file (a list with sub list)
     * */
    private val testFileList = listOf(
        0L, // empty file
        StorageLayer.DEFAULT_BLOB_SIZE - 3L, // small file
        3L * StorageLayer.DEFAULT_BLOB_SIZE, // middle file
        (StorageLayer.DEFAULT_LIST_LENGTH + 3L) * StorageLayer.DEFAULT_BLOB_SIZE, // big file
        3L * StorageLayer.DEFAULT_LIST_LENGTH * StorageLayer.DEFAULT_BLOB_SIZE, // large file
    )

    private fun makeTestFile(file: File, size: Long) {
        logger.info("Creating test file: $size byte(s)")
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

    @BeforeEach
    fun prepareTestEnv() {
        sourceDir.mkdirs()
        File(sourceDir, "empty dir").also { it.mkdirs() }
        val dir = File(sourceDir, "normal dir").also { it.mkdirs() }
        for (size in testFileList) {
            makeTestFile(File(dir, "${System.currentTimeMillis()}_${System.nanoTime()}"), size)
        }
        dataDir.mkdirs()
    }

    @AfterEach
    fun cleanUp() {
        sourceDir.deleteRecursively()
        dataDir.deleteRecursively()
    }

    /**
     * In this test we cannot check local file count, since random data
     * might be able to reuse blob.
     * */
    private fun testNativeStorage(storageLayer: FileNativeStorageLayer, obj: AritegObject) {
        storageLayer.walkTree(obj) { _, layer, path, proto ->
            logger.info(path)
            val targetFile = File(sourceDir, path.removePrefix("/"))
            val loadedHash = layer.readInputStreamFromProto(proto).use { sha3Provider512.digest(it, 4096 * 1024) }
            logger.info(loadedHash.toBase58())
            logger.info(targetFile.path)
            val targetHash = targetFile.inputStream().use { sha3Provider512.digest(it, 4096 * 1024) }
            logger.info(targetHash.toBase58())
            require(loadedHash == targetHash)
            logger.info("-".repeat(20))
        }
    }

    @Test
    fun testBlocking() {
        BlockingNativeStorageClient(dataDir).use { storageClient ->
            val storageLayer = FileNativeStorageLayer(storageClient)
            val obj = storageLayer.storeDir(sourceDir)
            testNativeStorage(storageLayer, obj)
        }
    }

    @Test
    fun testAsync() {
        AsyncNativeStorageClient(dataDir).use { storageClient ->
            val storageLayer = FileNativeStorageLayer(storageClient)
            val obj = storageLayer.storeDir(sourceDir)
            // since we are async and native storage read without check the status
            // we need to wait all writing jobs are done, then check result
            while (!storageClient.allClear()) Thread.yield()
            testNativeStorage(storageLayer, obj)
        }
    }
}
