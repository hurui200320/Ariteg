package info.skyblond.ariteg.slicers.rolling

import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.random.Random

internal class RabinKarpSlicerTest {
    @Test
    fun testUInt() {
        val u1 = 0u - 1u
        assertEquals(UInt.MAX_VALUE, u1)
        val u2 = UInt.MAX_VALUE + 1u
        assertEquals(UInt.MIN_VALUE, u2)
    }

    @Test
    fun test() {
        // 16KB data
        val content = ByteArray(16384)
        Random.nextBytes(content)
        val file = File.createTempFile("ariteg-blob", ".blob")
        file.deleteOnExit()
        FileUtils.writeByteArrayToFile(file, content)

        val blobs = RabinKarpSlicer(
            file, 0u, (1u shl 10) - 1u,
            64, 512, 48, 1821497u
        ).map { future ->
            future.get().also { it.file.deleteOnExit() }.readBlob()
        }.map { it.get().data }.onEach { assertTrue(it.size <= 512) }

        println(blobs.size)

        assertArrayEquals(content, blobs.reduceRight { current, acc ->
            current + acc
        })
    }

    @Test
    fun testOverflow() {
        // 16KB data
        val content = ByteArray(16384)
        Random.nextBytes(content)
        val file = File.createTempFile("ariteg-blob", ".blob")
        file.deleteOnExit()
        FileUtils.writeByteArrayToFile(file, content)

        val iterator = RabinKarpSlicer(
            file, 0u, (1u shl 10) - 1u,
            64, 512, 48, 1821497u
        ).iterator()

        while (iterator.hasNext()) {
            // eat blobs
            iterator.next().get().file.deleteOnExit()
        }

        assertThrows<NoSuchElementException> {
            iterator.next()
        }
    }
}
