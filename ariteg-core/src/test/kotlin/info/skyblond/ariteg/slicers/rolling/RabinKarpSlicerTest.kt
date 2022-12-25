package info.skyblond.ariteg.slicers.rolling

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
        // 16KB
        val content = ByteArray(16384)
        Random.nextBytes(content)
        val blobs = RabinKarpSlicer(
            0u, (1u shl 10) - 1u,
            64, 512, 48, 1821497u
        ).slice(content.inputStream()).map { it.data }
            .onEach { assertTrue(it.size <= 512) }
            .toList()
        println("Total ${blobs.size} blobs, last one is ${blobs.last().size} bytes")
        assertArrayEquals(content, blobs.reduceRight { current, acc ->
            current + acc
        })
    }
}
