package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import io.minio.errors.ErrorResponseException
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
            .endpoint("https://play.min.io")
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
            println(objs)
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
    fun testBlob() {
        val blob = Blob(Random.nextBytes(64))
        val link = minioStorage.write(Link.Type.BLOB, blob).get()
        val blobR = minioStorage.read(link).get() as Blob
        assertEquals(blob, blobR)
        minioStorage.delete(link).get()
        assertThrows<ExecutionException> {
            minioStorage.read(link).get()
        }.also {
            assertTrue { it.cause is ErrorResponseException }
            assertEquals("NoSuchKey", (it.cause as ErrorResponseException).errorResponse().code())
        }

        assertDoesNotThrow {
            minioStorage.delete(link)
        }
    }

    @Test
    fun testList() {
        val list = ListObject(listOf(Link("something", Link.Type.BLOB)))
        val link = minioStorage.write(Link.Type.LIST, list).get()
        val listR = minioStorage.read(link).get() as ListObject
        assertEquals(list, listR)
        minioStorage.delete(link).get()
        assertThrows<ExecutionException> {
            minioStorage.read(link).get()
        }.also {
            assertTrue { it.cause is ErrorResponseException }
            assertEquals("NoSuchKey", (it.cause as ErrorResponseException).errorResponse().code())
        }
    }

    @Test
    fun testTree() {
        val tree = TreeObject(listOf(Link("something", Link.Type.BLOB, "name")))
        val link = minioStorage.write(Link.Type.TREE, tree).get()
        val treeR = minioStorage.read(link).get() as TreeObject
        assertEquals(tree, treeR)
        minioStorage.delete(link).get()
        assertThrows<ExecutionException> {
            minioStorage.read(link).get()
        }.also {
            assertTrue { it.cause is ErrorResponseException }
            assertEquals("NoSuchKey", (it.cause as ErrorResponseException).errorResponse().code())
        }
    }

    @Test
    fun listObjects() {
        val blobs = (0..10).map { minioStorage.writeBlob(Blob(Random.nextBytes(32))).get() }
        val lists = (0..7).map { minioStorage.writeList(ListObject(listOf(blobs[it]))).get() }
        val trees = (0..3).map { minioStorage.writeTree(TreeObject(listOf(lists[it].copy(name = "name")))).get() }

        val (b, l, t) = minioStorage.listObjects().get()
        assertEquals(blobs.size, b.size)
        assertEquals(lists.size, l.size)
        assertEquals(trees.size, t.size)

        blobs.forEach { assertTrue { b.contains(it.hash) } }
        lists.forEach { assertTrue { l.contains(it.hash) } }
        trees.forEach { assertTrue { t.contains(it.hash) } }
    }

    @Test
    fun testEntry() {
        val entry = Entry("name", Link("hash", Link.Type.BLOB), Date())
        minioStorage.addEntry(entry).get()

        assertEquals(1, minioStorage.listEntry().count())

        minioStorage.removeEntry(entry).get()

        assertEquals(0, minioStorage.listEntry().count())

        assertDoesNotThrow {
            minioStorage.removeEntry(entry).get()
        }
    }
}
