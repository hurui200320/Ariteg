package info.skyblond.ariteg

import info.skyblond.ariteg.slicers.FixedSlicer
import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.storage.FileStorage
import info.skyblond.ariteg.storage.obj.Link
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OperationsTest {

    private val slicer: Slicer = FixedSlicer(1024) // 1k

    private lateinit var baseDir: File
    private lateinit var storage: FileStorage

    @BeforeEach
    internal fun setUp() {
        baseDir = File(FileUtils.getTempDirectory(), Random.nextLong().toString())
        storage = FileStorage(baseDir, Random.nextBytes(32))
        System.gc()
    }

    @AfterEach
    internal fun tearDown() {
        storage.close()
        FileUtils.forceDelete(baseDir)
    }

    private fun prepareTestRootFolder(): File =
        File(FileUtils.getTempDirectory(), Random.nextLong().toString()).also { r ->
            r.mkdirs()
            File(r, "tree-test").also { t ->
                t.mkdirs()
                File(t, "tree-in-tree").also { tt ->
                    tt.mkdirs()
                    File(tt, "hello").also {
                        FileUtils.writeByteArrayToFile(it, Random.nextBytes(2048))
                    }
                }
                File(t, "empty-folder-in-tree").also { tt ->
                    tt.mkdirs()
                }
                File(t, "hello2").also {
                    FileUtils.writeByteArrayToFile(it, Random.nextBytes(16 * 1024))
                }
            }
            File(r, "empty").also {
                FileUtils.writeByteArrayToFile(it, Random.nextBytes(0))
            }
            File(r, "small").also {
                FileUtils.writeByteArrayToFile(it, Random.nextBytes(128))
            }
            File(r, "big").also {
                FileUtils.writeByteArrayToFile(it, Random.nextBytes(256 * 1024 + 384))
            }
        }

    private fun listFile(root: File): List<File> = if (root.isFile) {
        listOf(root)
    } else {
        root.listFiles()!!.let { fs ->
            fs.filter { it.isFile } + fs.filter { it.isDirectory }.flatMap { listFile(it) }
        }
    }

    @Test
    fun testDigest(): Unit = runBlocking {
        // prepare root file
        val root = prepareTestRootFolder()
        // test digest & restore
        val entry = Operations.digest(root, slicer, storage)
        val recoveredRoot = File(FileUtils.getTempDirectory(), entry.name)
        Operations.restore(entry, storage, FileUtils.getTempDirectory())

        // recoveredRoot has the content in Root
        listFile(root).forEach {
            val path = it.canonicalPath.removePrefix(root.canonicalPath)
            val targetFile = File(recoveredRoot, path)
            assertTrue { FileUtils.contentEquals(it, targetFile) }
        }
        // root has the content in recovered root (no extra content in recovered root)
        listFile(recoveredRoot).forEach {
            val path = it.canonicalPath.removePrefix(recoveredRoot.canonicalPath)
            val targetFile = File(root, path)
            assertTrue { FileUtils.contentEquals(it, targetFile) }
        }
    }

    @Test
    fun gc(): Unit = runBlocking {
        // use preload to check nothing goes wrong
        // make sure unused things are deleted
        val root = prepareTestRootFolder()
        val entry = Operations.digest(root, slicer, storage)
        val b = storage.listObjects(Link.Type.BLOB).toList()
        val l = storage.listObjects(Link.Type.LIST).toList()
        val t = storage.listObjects(Link.Type.TREE).toList()

        val root2 = prepareTestRootFolder()
        val entry2 = Operations.digest(root2, slicer, storage)
        Operations.listEntry(storage).toList().also {
            assertEquals(2, it.size)
            assertTrue { it.contains(entry) }
            assertTrue { it.contains(entry2) }
        }

        Operations.deleteEntry(entry2, storage)
        Operations.listEntry(storage).toList().also {
            assertEquals(1, it.size)
            assertTrue { it.contains(entry) }
        }

        Operations.gc(storage)


        val b1 = storage.listObjects(Link.Type.BLOB).toList()
        val l1 = storage.listObjects(Link.Type.LIST).toList()
        val t1 = storage.listObjects(Link.Type.TREE).toList()
        assertEquals(b, b1)
        assertEquals(l, l1)
        assertEquals(t, t1)
        assertDoesNotThrow {
            runBlocking { Operations.resolve(entry, storage).forEach { storage.read(it) } }
        }

    }
}
