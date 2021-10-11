package info.skyblond.ariteg.proto.storage

import info.skyblond.ariteg.multihash.MultihashProviders
import info.skyblond.ariteg.proto.*
import info.skyblond.ariteg.proto.meta.mapdb.MapDBProtoMetaService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.io.File
import kotlin.random.Random

internal class NaiveS3StorageServiceTest {
    private val logger = LoggerFactory.getLogger(ProtoWriteServiceTest::class.java)
    private val defaultBlobSize = 4 * 1024 // 4KB
    private val defaultListSize = 32

    private val dataBaseDir = File("./data/test_data_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
    private val sha3Provider512 = MultihashProviders.sha3Provider512()

    private val s3Client: S3Client
    private val storageService: NaiveS3StorageService
    private val metaService: MapDBProtoMetaService
    private val protoService: ProtoWriteService

    init {
        dataBaseDir.mkdirs()
        s3Client = S3Client.builder()
            .region(Region.US_WEST_2)
            .httpClientBuilder(getProxyApacheClientBuilder())
            .build()
        storageService = NaiveS3StorageService(
            s3Client, "skyblond-ariteg-develop-test-202110", 16,
            MultihashProviders.sha3Provider512(), MultihashProviders.blake2b512Provider()
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
        // stop s3
        s3Client.close()
    }

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
}
