package info.skyblond.ariteg.proto

import info.skyblond.ariteg.multihash.MultihashProviders
import info.skyblond.ariteg.proto.meta.mapdb.MapDBProtoMetaService
import info.skyblond.ariteg.proto.storage.FileProtoStorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random


class ProtoWriteServiceTest {
    private val logger = LoggerFactory.getLogger(ProtoWriteServiceTest::class.java)
    private val defaultBlobSize = 4 * 1024 // 4KB
    private val defaultListSize = 32

    private val dataBaseDir = File("./data/test_data_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
    private val sha3Provider512 = MultihashProviders.sha3Provider512()

    private val storageService: FileProtoStorageService
    private val metaService: MapDBProtoMetaService
    private val protoService: ProtoWriteService

    init {
        dataBaseDir.mkdirs()
        storageService = FileProtoStorageService(
            dataBaseDir, 16, MultihashProviders.sha3Provider512(), MultihashProviders.blake2b512Provider()
        )
        metaService = MapDBProtoMetaService(File(dataBaseDir, "client.db"))
        protoService = object : ProtoWriteService(metaService, storageService, 5000) {}
    }

    @AfterEach
    fun cleanUp() {
        // close storage first, wait all task finished
        storageService.close()
        // then close the metadata db
        metaService.close()
        // clean files
        dataBaseDir.deleteRecursively()
    }

    private fun prepareTestFile(size: Long): File {
        val file = File.createTempFile(System.currentTimeMillis().toString(), System.nanoTime().toString())
        logger.info("Prepare test file: ${file.canonicalPath}, $size byte(s)")
        val buffer = ByteArray(4096) // 4KB
        file.outputStream().use { outputStream ->
            var counter = 0L
            while (counter < size) {
                Random.nextBytes(buffer)
                outputStream.write(buffer, 0, min(buffer.size.toLong(), size - counter).toInt())
                counter += buffer.size
            }
        }
        return file
    }

    /**
     * Basic function test
     * */
    @Test
    fun testChunkFunction() {
        listOf(
            0L, // empty file
            defaultBlobSize - 3L, // small file
            3L * defaultBlobSize, // middle file
            (defaultListSize + 3L) * defaultBlobSize, // big file
            3L * defaultListSize * defaultBlobSize, // large file
        ).forEach { size ->
            // prepare test file
            val file = prepareTestFile(size)
            val targetHash = file.inputStream().use { sha3Provider512.digest(it, 4096 * 1024) }
            logger.info("Test file hash: ${targetHash.toBase58()}")
            val (link, futureList) = file.inputStream()
                .use { input -> protoService.writeChunk("", input, defaultBlobSize, defaultListSize) }
            // wait all writing is finished
            Assertions.assertDoesNotThrow {
                futureList.forEach { it.get() }
            }

            val loadedHash = simpleRead(storageService, link).use { sha3Provider512.digest(it, 4096 * 1024) }
            Assertions.assertEquals(targetHash, loadedHash)
        }
    }

    @Test
    fun testChunkMultiThread() {
        val pieceCount = defaultListSize + 1
        val file = prepareTestFile(defaultBlobSize.toLong() * pieceCount)
        val threadCount = 80
        val executor = Executors.newCachedThreadPool()
        val barrier = CyclicBarrier(threadCount + 1)
        val futureList = (1..threadCount).map {
            executor.submit(Callable {
                file.inputStream().use {
                    barrier.await()
                    // start and the same time
                    protoService.writeChunk("", it, defaultBlobSize, defaultListSize)
                }
            })
        }
        barrier.await()
        logger.info("Barrier passed")

        val result = futureList.map { task ->
            task.get()
        }
        Assertions.assertEquals(1, result.map { it.first }.distinct().size)
        Assertions.assertDoesNotThrow {
            // check every writing request
            result.flatMap { it.second }.forEach { it.get() }
        }
        // extra 1 for additional list objects
        // link -> list -> (list, blob)
        //                    |
        //                    +-> (blob x listSize)
        // Be careful with the type
        Assertions.assertEquals(pieceCount + 2L, storageService.writeCounter.get())
        logger.info("Done, write count: ${storageService.writeCounter.get()}")
        executor.shutdown()
    }
}
