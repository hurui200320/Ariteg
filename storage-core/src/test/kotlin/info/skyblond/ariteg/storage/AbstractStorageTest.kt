package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals

internal abstract class AbstractStorageTest {
    protected abstract val storage: InMemoryStorage

    @AfterEach
    internal fun tearDown() {
        storage.close()
    }

    @Test
    fun testBlob(): Unit = runBlocking {
        val blob = Blob(Random.nextBytes(64))
        val link = storage.write(Link.Type.BLOB, blob)
        assertEquals(64, link.size)
        val link2 = assertDoesNotThrow { runBlocking { storage.write(Link.Type.BLOB, blob) } }
        assertEquals(link, link2)
        val blobR = storage.read(link) as Blob
        assertEquals(blob, blobR)
        storage.delete(link)
        assertThrows<ObjectNotFoundException> {
            runBlocking { storage.read(link) }
        }

        assertDoesNotThrow {
            runBlocking { storage.delete(link) }
        }
    }

    @Test
    fun testList(): Unit = runBlocking {
        val list = ListObject(listOf(Link("something", Link.Type.BLOB, -1)))
        val link = storage.write(Link.Type.LIST, list)
        val listR = storage.read(link) as ListObject
        assertEquals(list, listR)
        storage.delete(link)
        assertThrows<ObjectNotFoundException> {
            runBlocking { storage.read(link) }
        }
    }

    @Test
    fun testTree(): Unit = runBlocking {
        val tree = TreeObject(mapOf("name" to Link("something", Link.Type.BLOB, -1)))
        val link = storage.write(Link.Type.TREE, tree)
        val treeR = storage.read(link) as TreeObject
        assertEquals(tree, treeR)
        storage.delete(link)
        assertThrows<ObjectNotFoundException> {
            runBlocking { storage.read(link) }
        }
    }

    @Test
    fun testResolve(): Unit = runBlocking {
        val blobLinks = mutableListOf<Link>()
        for (i in 1..3) {
            blobLinks.add(storage.write(Link.Type.BLOB, Blob(Random.nextBytes(32))))
        }
        var link = storage.write(Link.Type.LIST, ListObject(blobLinks))
        link = storage.write(Link.Type.TREE, TreeObject(mapOf("name" to link)))

        val result = storage.resolve(link).toList()
        // 3 blobs + 1 list + 1 tree
        assertEquals(blobLinks.toSet().size + 2, result.size)
    }

    @Test
    fun testEntry(): Unit = runBlocking {
        val entry = Entry("name", Link("hash", Link.Type.BLOB, -1), Date())
        storage.addEntry(entry)
        val restored = storage.getEntry(entry.name)
        assertEquals(entry.name, restored.name)
        assertEquals(entry.root, restored.root)
        assertEquals(entry.ctime, restored.ctime)

        assertEquals(1, storage.listEntry().count())

        val entry2 = storage.addEntry(entry)
        assertEquals(2, storage.listEntry().count())
        assertEquals("dup_name", entry2.name)
        println(entry2)

        storage.removeEntry(entry.name)

        assertEquals(1, storage.listEntry().count())

        assertDoesNotThrow {
            runBlocking { storage.removeEntry(entry.name) }
        }
    }
}
