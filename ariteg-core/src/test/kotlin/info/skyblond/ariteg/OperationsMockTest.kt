package info.skyblond.ariteg

import info.skyblond.ariteg.storage.Storage
import info.skyblond.ariteg.storage.obj.Entry
import info.skyblond.ariteg.storage.obj.Link
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.ZonedDateTime
import java.util.*

/**
 * This test make sure the [Operations] call the right api.
 * */
class OperationsMockTest {

    private lateinit var storage: Storage

    @BeforeEach
    internal fun setUp() {
        storage = mock(Storage::class.java)
    }


    @Test
    fun testResolve(): Unit = runBlocking {
        val link = Link("Something", Link.Type.BLOB, -1)
        `when`(storage.resolve(link)).thenReturn(emptySequence())
        Operations.resolve(Entry("name", link, ZonedDateTime.now()), storage)
        verify(storage, times(1)).resolve(link)
    }
}
