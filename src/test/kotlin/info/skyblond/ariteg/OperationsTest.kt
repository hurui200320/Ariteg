package info.skyblond.ariteg

import info.skyblond.ariteg.slicers.FixedSlicer
import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.storage.FileStorage
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OperationsTest {

    private val slicerProvider: (File) -> Slicer = { file ->
        FixedSlicer(file, 1024) // 1k
    }

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
        root.listFiles()!!.let { fs->
            fs.filter { it.isFile } + fs.filter { it.isDirectory }.flatMap { listFile(it) }
        }
    }

    @Test
    fun testDigest() {
        // prepare root file
        val root = prepareTestRootFolder()
        // test digest & restore
        val entry = Operations.digest(root, slicerProvider, storage)
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
    fun gc() {
        // use preload to check nothing goes wrong
        // make sure unused things are deleted
        val root = prepareTestRootFolder()
        val entry = Operations.digest(root, slicerProvider, storage)
        val (b,l,t) = storage.listObjects().get()

        val root2 = prepareTestRootFolder()
        val entry2 = Operations.digest(root2, slicerProvider, storage)
        Operations.listEntry(storage).also {
            assertEquals(2, it.size)
            assertTrue { it.contains(entry) }
            assertTrue { it.contains(entry2) }
        }

        Operations.deleteEntry(entry2, storage)
        Operations.listEntry(storage).also {
            assertEquals(1, it.size)
            assertTrue { it.contains(entry) }
        }

        Operations.gc(storage)

        val (b1,l1,t1) = storage.listObjects().get()
        assertEquals(b, b1)
        assertEquals(l, l1)
        assertEquals(t, t1)
        assertDoesNotThrow {
            Operations.preload(entry, storage)
        }

    }
}
