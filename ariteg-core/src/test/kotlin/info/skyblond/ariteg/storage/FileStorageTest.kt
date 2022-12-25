package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import info.skyblond.ariteg.Link.Type.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.*
import java.io.File
import java.nio.file.NoSuchFileException
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
    fun testBlob(): Unit = runBlocking {
        val blob = Blob(Random.nextBytes(64))
        val link = fileStorage.write(BLOB, blob)
        assertEquals(64, link.size)
        val blobR = fileStorage.read(link) as Blob
        assertEquals(blob, blobR)
        fileStorage.delete(link)
        assertThrows<NoSuchFileException> {
            runBlocking { fileStorage.read(link) }
        }

        assertDoesNotThrow {
            runBlocking { fileStorage.delete(link) }
        }
    }

    @Test
    fun testList(): Unit = runBlocking {
        val list = ListObject(listOf(Link("something", BLOB, -1)))
        val link = fileStorage.write(LIST, list)
        val listR = fileStorage.read(link) as ListObject
        assertEquals(list, listR)
        fileStorage.delete(link)
        assertThrows<NoSuchFileException> {
            runBlocking { fileStorage.read(link) }
        }
    }

    @Test
    fun testTree(): Unit = runBlocking {
        val tree = TreeObject(listOf(Link("something", BLOB, -1, "name")))
        val link = fileStorage.write(TREE, tree)
        val treeR = fileStorage.read(link) as TreeObject
        assertEquals(tree, treeR)
        fileStorage.delete(link)
        assertThrows<NoSuchFileException> {
            runBlocking { fileStorage.read(link) }
        }
    }

    @Test
    fun testResolve(): Unit = runBlocking {
        val blobLinks = mutableListOf<Link>()
        for (i in 1..3) {
            blobLinks.add(fileStorage.write(BLOB, Blob(Random.nextBytes(32))))
        }
        var link = fileStorage.write(LIST, ListObject(blobLinks)).copy(name = "name")
        link = fileStorage.write(TREE, TreeObject(listOf(link)))

        val result = fileStorage.resolve(link)

        assertEquals(blobLinks.toSet().size, result.size)
    }

    @Test
    fun testEntry(): Unit = runBlocking {
        val entry = Entry("name", Link("hash", BLOB, -1), Date())
        fileStorage.addEntry(entry)

        assertEquals(1, fileStorage.listEntry().count())

        fileStorage.removeEntry(entry.id)

        assertEquals(0, fileStorage.listEntry().count())

        assertDoesNotThrow {
            runBlocking { fileStorage.removeEntry(entry.id) }
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
    fun testListObjects(): Unit = runBlocking {
        val blobs = (0..30).map { fileStorage.write(BLOB, Blob(Random.nextBytes(32))) }
        val lists = (0..10).map { fileStorage.write(LIST, ListObject(listOf(blobs[it]))) }
        val trees = (0..3).map { fileStorage.write(TREE, TreeObject(listOf(lists[it].copy(name = "name")))) }

        val (b, l, t) = fileStorage.listObjects()
        assertEquals(blobs.size, b.size)
        assertEquals(lists.size, l.size)
        assertEquals(trees.size, t.size)

        blobs.forEach { assertTrue { b.contains(it.hash) } }
        lists.forEach { assertTrue { l.contains(it.hash) } }
        trees.forEach { assertTrue { t.contains(it.hash) } }
    }
}
