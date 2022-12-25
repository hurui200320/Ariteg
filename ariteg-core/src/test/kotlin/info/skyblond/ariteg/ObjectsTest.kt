package info.skyblond.ariteg

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class ObjectsTest {
    @Test
    fun testHash() = runBlocking {
        val aritegObjects = listOf<AritegObject>(
            Blob(ByteArray(10)),
            ListObject(emptyList()),
            TreeObject(emptyList())
        )

        aritegObjects.forEach {
            val hash = it.getHashString()
            assertDoesNotThrow { runBlocking { it.verify(hash) } }
        }
    }

    @Test
    fun testBlobEqualAndHashcode() {
        val blob1 = Blob(ByteArray(10))
        val blob2 = Blob(ByteArray(10))
        assertTrue(blob1 == blob2)
        assertTrue(blob1.hashCode() == blob2.hashCode())
    }

    @Test
    fun testNamedLinkInList() {
        assertDoesNotThrow {
            ListObject(listOf(Link.blobRef("")))
        }
        assertThrows<IllegalArgumentException> {
            ListObject(listOf(Link.blobRef("", "name")))
        }
    }

    @Test
    fun testUnnamedLinkInTree() {
        assertDoesNotThrow {
            TreeObject(listOf(Link.blobRef("", "name")))
        }
        assertThrows<IllegalArgumentException> {
            TreeObject(listOf(Link.blobRef("")))
        }
    }

    @Test
    fun testFromToJson() {
        // List, Tree, Entry
        val listObj = ListObject(listOf(Link("hash", Link.Type.BLOB, 2345)))
        val listObjJson = listObj.toJson()
        assertEquals("{\"content\":[{\"hash\":\"hash\",\"type\":\"BLOB\",\"size\":2345}]}", listObjJson)
        val listObjDes = ListObject.fromJson(listObjJson)
        assertEquals(listObj, listObjDes)

        val treeObj = TreeObject(listOf(Link("hash", Link.Type.BLOB, 1234, "name")))
        val treeObjJson = treeObj.toJson()
        assertEquals(
            "{\"content\":[{\"hash\":\"hash\",\"type\":\"BLOB\",\"size\":1234,\"name\":\"name\"}]}",
            treeObjJson
        )
        val treeObjDes = TreeObject.fromJson(treeObjJson)
        assertEquals(treeObj, treeObjDes)

        val entry = Entry("文件名", Link("hash", Link.Type.BLOB, 3456), Date(1656040508353), "note", "id")
        val entryJson = entry.toJson()
        val entryDes = Entry.fromJson(entryJson)
        assertEquals(entry, entryDes)
    }

    @Test
    fun testEncodeToByte() {
        val blob = Blob(ByteArray(10) { it.toByte() })
        assertArrayEquals(blob.data, blob.encodeToBytes())

        val listObj = ListObject(listOf(Link("hash", Link.Type.BLOB, -1)))
        assertArrayEquals(listObj.toJson().encodeToByteArray(), listObj.encodeToBytes())

        val treeObj = TreeObject(listOf(Link("hash", Link.Type.BLOB, -1, "name")))
        assertArrayEquals(treeObj.toJson().encodeToByteArray(), treeObj.encodeToBytes())
    }
}
