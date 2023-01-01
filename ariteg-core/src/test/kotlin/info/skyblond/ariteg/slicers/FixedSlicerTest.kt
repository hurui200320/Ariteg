package info.skyblond.ariteg.slicers

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

internal class FixedSlicerTest {
    @Test
    fun test1() {
        // 16KB data
        val content = ByteArray(16384)
        Random.nextBytes(content)

        val blobs = FixedSlicer(126)
            .slice(content.inputStream())
            .map { it.data }
            .toList()

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

        val blobs = FixedSlicer(128)
            .slice(content.inputStream()).map { it.data }
            .onEach { assertEquals(128, it.size) }
            .toList()

        assertEquals(128, blobs.size)
        assertArrayEquals(content, blobs.reduceRight { current, acc ->
            current + acc
        })
    }
}
