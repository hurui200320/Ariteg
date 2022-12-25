package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import io.minio.errors.ErrorResponseException
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.*
import java.util.*
import java.util.concurrent.ExecutionException
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
    fun testBlob() = runBlocking {
        val blob = Blob(Random.nextBytes(64))
        val link = minioStorage.write(Link.Type.BLOB, blob)
        assertEquals(64, link.size)
        val blobR = minioStorage.read(link) as Blob
        assertEquals(blob, blobR)
        minioStorage.delete(link)
        assertThrows<ErrorResponseException> {
            runBlocking { minioStorage.read(link) }
        }.also {
            assertEquals("NoSuchKey", it.errorResponse().code())
        }

        assertDoesNotThrow {
            runBlocking { minioStorage.delete(link) }
        }
    }

    @Test
    fun testList(): Unit = runBlocking {
        val list = ListObject(listOf(Link("something", Link.Type.BLOB, -1)))
        val link = minioStorage.write(Link.Type.LIST, list)
        val listR = minioStorage.read(link) as ListObject
        assertEquals(list, listR)
        minioStorage.delete(link)
        assertThrows<ErrorResponseException> {
            runBlocking { minioStorage.read(link) }
        }.also {
            assertEquals("NoSuchKey", it.errorResponse().code())
        }
    }

    @Test
    fun testTree(): Unit = runBlocking {
        val tree = TreeObject(listOf(Link("something", Link.Type.BLOB, -1, "name")))
        val link = minioStorage.write(Link.Type.TREE, tree)
        val treeR = minioStorage.read(link) as TreeObject
        assertEquals(tree, treeR)
        minioStorage.delete(link)
        assertThrows<ErrorResponseException> {
            runBlocking { minioStorage.read(link) }
        }.also {
            assertEquals("NoSuchKey", it.errorResponse().code())
        }
    }

    @Test
    fun listObjects(): Unit = runBlocking {
        val blobs = (0..10).map { minioStorage.write(Link.Type.BLOB, Blob(Random.nextBytes(32))) }
        val lists = (0..7).map { minioStorage.write(Link.Type.LIST, ListObject(listOf(blobs[it]))) }
        val trees =
            (0..3).map { minioStorage.write(Link.Type.TREE, TreeObject(listOf(lists[it].copy(name = "name")))) }

        val (b, l, t) = minioStorage.listObjects()
        assertEquals(blobs.size, b.size)
        assertEquals(lists.size, l.size)
        assertEquals(trees.size, t.size)

        blobs.forEach { assertTrue { b.contains(it.hash) } }
        lists.forEach { assertTrue { l.contains(it.hash) } }
        trees.forEach { assertTrue { t.contains(it.hash) } }
    }

    @Test
    fun testEntry() = runBlocking {
        val entry = Entry("name", Link("hash", Link.Type.BLOB, -1), Date())
        minioStorage.addEntry(entry)

        assertEquals(1, minioStorage.listEntry().count())

        minioStorage.removeEntry(entry.id)

        assertEquals(0, minioStorage.listEntry().count())

        assertDoesNotThrow {
            runBlocking { minioStorage.removeEntry(entry.id) }
        }
    }
}
