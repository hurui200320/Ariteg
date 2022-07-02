package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
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

    lateinit var baseDir: File
    lateinit var fileStorage: FileStorage

    @BeforeEach
    internal fun setUp() {
        baseDir = File(FileUtils.getTempDirectory(), Random.nextLong().toString())
        fileStorage = FileStorage(baseDir)
    }

    @AfterEach
    internal fun tearDown() {
        fileStorage.close()
        FileUtils.forceDelete(baseDir)
    }

    @Test
    fun testBlob() {
        val blob = Blob(Random.nextBytes(64))
        val link = fileStorage.write(Link.Type.BLOB, blob).get()
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
        val list = ListObject(listOf(Link("something", Link.Type.BLOB)))
        val link = fileStorage.write(Link.Type.LIST, list).get()
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
    fun writeTree() {
        val tree = TreeObject(listOf(Link("something", Link.Type.BLOB, "name")))
        val link = fileStorage.write(Link.Type.TREE, tree).get()
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
            blobLinks.add(fileStorage.writeBlob(Blob(Random.nextBytes(32))).get())
        }
        var link = fileStorage.write(Link.Type.LIST, ListObject(blobLinks)).get().copy(name = "name")
        link = fileStorage.write(Link.Type.TREE, TreeObject(listOf(link))).get()

        val result = fileStorage.resolve(link).get()

        assertEquals(blobLinks.toSet().size, result.size)
    }

    @Test
    fun testRecover() {
        val blobLinks = mutableListOf<Link>()
        for (i in 1..3) {
            blobLinks.add(fileStorage.writeBlob(Blob(Random.nextBytes(32))).get())
        }
        var link = fileStorage.write(Link.Type.LIST, ListObject(blobLinks)).get().copy(name = "name")
        link = fileStorage.write(Link.Type.TREE, TreeObject(listOf(link))).get()

        assertDoesNotThrow {
            fileStorage.recover(setOf(link)).get()
        }

        assertThrows<ExecutionException> {
            fileStorage.recover(setOf(Link("something", Link.Type.BLOB))).get()
        }.also {
            assertTrue { it.cause is IllegalStateException }
        }
    }

    @Test
    fun testEntry() {
        val entry = Entry("name", Link("hash", Link.Type.BLOB), Date())
        fileStorage.addEntry(entry).get()

        assertEquals(1, fileStorage.listEntry().count())

        fileStorage.removeEntry(entry).get()
        fileStorage.removeEntry(entry).get()

        File(baseDir, "entry").let {
            require(FileUtils.deleteQuietly(it))
            require(it.createNewFile())
        }

        assertThrows<IllegalStateException> {
            fileStorage.listEntry().count()
        }
    }

}
