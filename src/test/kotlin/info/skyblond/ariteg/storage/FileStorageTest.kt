package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import info.skyblond.ariteg.Link.Type.*
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FileStorageTest {

    private lateinit var baseDir: File
    private lateinit var fileStorage: FileStorage

    @BeforeEach
    internal fun setUp() {
        baseDir = File(FileUtils.getTempDirectory(), Random.nextLong().toString())
        fileStorage = FileStorage(baseDir, Random.nextBytes(32))
    }

    @AfterEach
    internal fun tearDown() {
        fileStorage.close()
        FileUtils.forceDelete(baseDir)
    }

    @Test
    fun testBlob() {
        val blob = Blob(Random.nextBytes(64))
        val link = fileStorage.write(BLOB, blob).get()
        val blobR = fileStorage.read(link).get() as Blob
        assertEquals(blob, blobR)
        fileStorage.delete(link).get()
        assertThrows<ExecutionException> {
            fileStorage.read(link).get()
        }.also {
            assertTrue { it.cause is FileNotFoundException }
        }

        assertDoesNotThrow {
            fileStorage.delete(link)
        }
    }

    @Test
    fun testList() {
        val list = ListObject(listOf(Link("something", BLOB)))
        val link = fileStorage.write(LIST, list).get()
        val listR = fileStorage.read(link).get() as ListObject
        assertEquals(list, listR)
        fileStorage.delete(link).get()
        assertThrows<ExecutionException> {
            fileStorage.read(link).get()
        }.also {
            assertTrue { it.cause is FileNotFoundException }
        }
    }

    @Test
    fun testTree() {
        val tree = TreeObject(listOf(Link("something", BLOB, "name")))
        val link = fileStorage.write(TREE, tree).get()
        val treeR = fileStorage.read(link).get() as TreeObject
        assertEquals(tree, treeR)
        fileStorage.delete(link).get()
        assertThrows<ExecutionException> {
            fileStorage.read(link).get()
        }.also {
            assertTrue { it.cause is FileNotFoundException }
        }
    }

    @Test
    fun testResolve() {
        val blobLinks = mutableListOf<Link>()
        for (i in 1..3) {
            blobLinks.add(fileStorage.write(BLOB, Blob(Random.nextBytes(32))).get())
        }
        var link = fileStorage.write(LIST, ListObject(blobLinks)).get().copy(name = "name")
        link = fileStorage.write(TREE, TreeObject(listOf(link))).get()

        val result = fileStorage.resolve(link).get()

        assertEquals(blobLinks.toSet().size, result.size)
    }

    @Test
    fun testEntry() {
        val entry = Entry("name", Link("hash", BLOB), Date())
        fileStorage.addEntry(entry).get()

        assertEquals(1, fileStorage.listEntry().count())

        fileStorage.removeEntry(entry).get()

        assertEquals(0, fileStorage.listEntry().count())

        assertDoesNotThrow {
            fileStorage.removeEntry(entry).get()
        }

        File(baseDir, "entry").let {
            require(FileUtils.deleteQuietly(it))
            require(it.createNewFile())
        }

        assertThrows<IllegalStateException> {
            fileStorage.listEntry().count()
        }
    }

    @Test
    fun testListObjects() {
        val blobs = (0..30).map { fileStorage.write(BLOB, Blob(Random.nextBytes(32))).get() }
        val lists = (0..10).map { fileStorage.write(LIST, ListObject(listOf(blobs[it]))).get() }
        val trees = (0..3).map { fileStorage.write(TREE, TreeObject(listOf(lists[it].copy(name = "name")))).get() }

        val (b, l, t) = fileStorage.listObjects().get()
        assertEquals(blobs.size, b.size)
        assertEquals(lists.size, l.size)
        assertEquals(trees.size, t.size)

        blobs.forEach { assertTrue { b.contains(it.hash) } }
        lists.forEach { assertTrue { l.contains(it.hash) } }
        trees.forEach { assertTrue { t.contains(it.hash) } }
    }
}
