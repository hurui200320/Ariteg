package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.*
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class MinioStorageTest {
    private val logger = KotlinLogging.logger { }

    private lateinit var minioStorage: MinioStorage
    private lateinit var minioClient: MinioClient
    private lateinit var bucketName: String

    @BeforeEach
    internal fun setUp() {
        minioClient = MinioClient.builder()
            .endpoint("play.min.io")
            .credentials("Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG")
            .build()
        bucketName = "ariteg-test-bucket-${System.currentTimeMillis()}-${Random.nextLong()}"
        minioStorage = MinioStorage(minioClient, bucketName, Random.nextBytes(32))
    }

    @AfterEach
    internal fun tearDown() {
        logger.info { "Cleaning up..." }
        minioStorage.close()

        while (true) {
            val objs = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .recursive(true)
                    .build()
            ).map { it.get().objectName() }
            if (objs.isEmpty()) {
                break
            }
            objs.forEach {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(it)
                        .build()
                )
            }
        }

        minioClient.removeBucket(
            RemoveBucketArgs.builder()
                .bucket(bucketName)
                .build()
        )

    }

    @Test
    fun testBlob(): Unit = runBlocking {
        val blob = Blob(Random.nextBytes(128))
        val link = blob.link
        assertThrows<ObjectNotFoundException> { runBlocking { minioStorage.read(link) } }
        minioStorage.write(Link.Type.BLOB, blob)
        assertDoesNotThrow { runBlocking { minioStorage.read(link) } }
        assertDoesNotThrow { runBlocking { minioStorage.write(Link.Type.BLOB, blob) } }
    }

    @Test
    fun testEntry(): Unit = runBlocking {
        val entry = Entry("name", Link("hash", Link.Type.BLOB, -1), ZonedDateTime.now())
        minioStorage.addEntry(entry)
        val restored = minioStorage.getEntry(entry.name)
        assertEquals(entry.name, restored.name)
        assertEquals(entry.root, restored.root)
        assertEquals(entry.ctime, restored.ctime)

        assertEquals(1, minioStorage.listEntry().count())

        minioStorage.removeEntry(entry.name)

        assertEquals(0, minioStorage.listEntry().count())

        assertDoesNotThrow {
            runBlocking { minioStorage.removeEntry(entry.name) }
        }
    }

    @Test
    fun testListObjects(): Unit = runBlocking {
        val blobs = (0..30).map { minioStorage.write(Link.Type.BLOB, Blob(Random.nextBytes(32))) }
        val lists = (0..10).map { minioStorage.write(Link.Type.LIST, ListObject(listOf(blobs[it]))) }
        val trees = (0..3).map { minioStorage.write(Link.Type.TREE, TreeObject(mapOf("name" to lists[it]))) }

        val b = minioStorage.listObjects(Link.Type.BLOB)
        val l = minioStorage.listObjects(Link.Type.LIST)
        val t = minioStorage.listObjects(Link.Type.TREE)
        assertEquals(blobs.size, b.count())
        assertEquals(lists.size, l.count())
        assertEquals(trees.size, t.count())

        b.forEach { assertTrue { blobs.contains(it) } }
        l.forEach { assertTrue { lists.contains(it) } }
        t.forEach { assertTrue { trees.contains(it) } }
    }
}
