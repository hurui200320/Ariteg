package info.skyblond.ariteg

import info.skyblond.ariteg.storage.Storage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*
import java.util.concurrent.CompletableFuture

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
    fun testResolve() {
        val link = Link("Something", Link.Type.BLOB, -1)
        `when`(storage.resolve(link)).thenReturn(CompletableFuture.supplyAsync { emptySet() })
        Operations.resolve(Entry("name", link, Date()), storage)
        verify(storage, times(1)).resolve(link)
    }
}
