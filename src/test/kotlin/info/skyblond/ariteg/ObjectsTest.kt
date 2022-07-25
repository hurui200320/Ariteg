package info.skyblond.ariteg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class ObjectsTest {
    @Test
    fun testHash() {
        val aritegObjects = listOf<AritegObject>(
            Blob(ByteArray(10)),
            ListObject(emptyList()),
            TreeObject(emptyList())
        )

        aritegObjects.forEach {
            val hash = it.getHashString().get()
            assertDoesNotThrow { it.verify(hash) }
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
            ListObject(listOf(Link("", Link.Type.BLOB)))
        }
        assertThrows<IllegalArgumentException> {
            ListObject(listOf(Link("", Link.Type.BLOB, "name")))
        }
    }

    @Test
    fun testUnnamedLinkInTree() {
        assertDoesNotThrow {
            TreeObject(listOf(Link("", Link.Type.BLOB, "name")))
        }
        assertThrows<IllegalArgumentException> {
            TreeObject(listOf(Link("", Link.Type.BLOB)))
        }
    }

    @Test
    fun testFromToJson() {
        // List, Tree, Entry
        val listObj = ListObject(listOf(Link("hash", Link.Type.BLOB)))
        val listObjJson = listObj.toJson()
        assertEquals("{\"content\":[{\"hash\":\"hash\",\"type\":\"BLOB\"}]}", listObjJson)
        val listObjDes = ListObject.fromJson(listObjJson)
        assertEquals(listObj, listObjDes)

        val treeObj = TreeObject(listOf(Link("hash", Link.Type.BLOB, "name")))
        val treeObjJson = treeObj.toJson()
        assertEquals("{\"content\":[{\"hash\":\"hash\",\"type\":\"BLOB\",\"name\":\"name\"}]}", treeObjJson)
        val treeObjDes = TreeObject.fromJson(treeObjJson)
        assertEquals(treeObj, treeObjDes)

        val entry = Entry("文件名", Link("hash", Link.Type.BLOB), Date(1656040508353), "note", "id")
        val entryJson = entry.toJson()
        assertEquals(
            "{  \"name\" : \"文件名\",  \"link\" : {    \"hash\" : \"hash\",    \"type\" : \"BLOB\"  },  \"time\" : \"2022-06-24T03:15:08.353+00:00\",  \"note\" : \"note\",  \"id\" : \"id\"}",
            entryJson.replace("\r\n", "")
        )
        val entryDes = Entry.fromJson(entryJson)
        assertEquals(entry, entryDes)
    }

    @Test
    fun testEncodeToByte() {
        val blob = Blob(ByteArray(10) { it.toByte() })
        assertArrayEquals(blob.data, blob.encodeToBytes())

        val listObj = ListObject(listOf(Link("hash", Link.Type.BLOB)))
        assertArrayEquals(listObj.toJson().encodeToByteArray(), listObj.encodeToBytes())

        val treeObj = TreeObject(listOf(Link("hash", Link.Type.BLOB, "name")))
        assertArrayEquals(treeObj.toJson().encodeToByteArray(), treeObj.encodeToBytes())
    }
}
