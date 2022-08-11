package info.skyblond.ariteg.slicers

import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals

internal class FixedSlicerTest {
    @Test
    fun test1() {
        // 16KB data
        val content = ByteArray(16384)
        Random.nextBytes(content)
        val file = File.createTempFile("ariteg-blob", ".blob")
        file.deleteOnExit()
        FileUtils.writeByteArrayToFile(file, content)

        val blobs = FixedSlicer(file, 126).map { it.data }

        assertEquals(131, blobs.size)
        assertArrayEquals(content, blobs.reduceRight { current, acc ->
            current + acc
        })
    }

    @Test
    fun test2() {
        // 16KB data
        val content = ByteArray(16384)
        Random.nextBytes(content)
        val file = File.createTempFile("ariteg-blob", ".blob")
        file.deleteOnExit()
        FileUtils.writeByteArrayToFile(file, content)

        val blobs = FixedSlicer(file, 128).map { it.data }
            .onEach { assertEquals(128, it.size) }

        assertEquals(128, blobs.size)
        assertArrayEquals(content, blobs.reduceRight { current, acc ->
            current + acc
        })
    }

    @Test
    fun testOverflow() {
        val content = ByteArray(16384)
        Random.nextBytes(content)
        val file = File.createTempFile("ariteg-blob", ".blob")
        file.deleteOnExit()
        FileUtils.writeByteArrayToFile(file, content)

        val slicer = FixedSlicer(file, 128).iterator()

        for (i in 1..128) {
            // eat blobs
            slicer.next()
        }

        assertThrows<NoSuchElementException> {
            slicer.next()
        }
    }
}
