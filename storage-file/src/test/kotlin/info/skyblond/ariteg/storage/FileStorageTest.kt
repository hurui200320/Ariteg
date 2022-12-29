package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.*
import java.io.File
import java.time.ZonedDateTime
import java.util.*
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
        val blob = Blob(Random.nextBytes(128))
        val link = blob.link
        assertThrows<ObjectNotFoundException> { runBlocking { fileStorage.read(link) } }
        fileStorage.write(Link.Type.BLOB, blob)
        assertDoesNotThrow { runBlocking { fileStorage.read(link) } }
        assertDoesNotThrow { runBlocking { fileStorage.write(Link.Type.BLOB, blob) } }
    }

    @Test
    fun testEntry(): Unit = runBlocking {
        val entry = Entry("name", Link("hash", Link.Type.BLOB, -1), ZonedDateTime.now())
        fileStorage.addEntry(entry)
        val restored = fileStorage.getEntry(entry.name)
        assertEquals(entry.name, restored.name)
        assertEquals(entry.root, restored.root)
        assertEquals(entry.ctime, restored.ctime)

        assertEquals(1, fileStorage.listEntry().count())

        fileStorage.removeEntry(entry.name)

        assertEquals(0, fileStorage.listEntry().count())

        assertDoesNotThrow {
            runBlocking { fileStorage.removeEntry(entry.name) }
        }

        File(baseDir, "entry").let {
            require(FileUtils.deleteQuietly(it))
            require(it.createNewFile())
        }
        // failed if is a file, instead of a dir
        assertThrows<IllegalStateException> {
            fileStorage.listEntry().count()
        }
    }

    @Test
    fun testListObjects(): Unit = runBlocking {
        val blobs = (0..30).map { fileStorage.write(Link.Type.BLOB, Blob(Random.nextBytes(32))) }
        val lists = (0..10).map { fileStorage.write(Link.Type.LIST, ListObject(listOf(blobs[it]))) }
        val trees = (0..3).map { fileStorage.write(Link.Type.TREE, TreeObject(mapOf("name" to lists[it]))) }

        val b = fileStorage.listObjects(Link.Type.BLOB)
        val l = fileStorage.listObjects(Link.Type.LIST)
        val t = fileStorage.listObjects(Link.Type.TREE)
        assertEquals(blobs.size, b.count())
        assertEquals(lists.size, l.count())
        assertEquals(trees.size, t.count())

        b.forEach { assertTrue { blobs.contains(it) } }
        l.forEach { assertTrue { lists.contains(it) } }
        t.forEach { assertTrue { trees.contains(it) } }
    }
}
