package info.skyblond.ariteg.slicers

import info.skyblond.ariteg.Blob
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutionException
import kotlin.random.Random

internal class BlobDescriptorTest {
    @Test
    fun test() {
        val content = ByteArray(128)
        Random.nextBytes(content)

        val blobDescriptor = BlobDescriptor.writeBlob(Blob(content)).get()
        blobDescriptor.file.deleteOnExit()
        val readContent = blobDescriptor.readBlob().get().data
        assertArrayEquals(content, readContent)

        assertThrows<ExecutionException> {
            BlobDescriptor("hash", blobDescriptor.file).readBlob().get()
        }.also {
            assertNotNull(it.cause)
            assertTrue(it.cause is IllegalStateException)
        }
    }
}
