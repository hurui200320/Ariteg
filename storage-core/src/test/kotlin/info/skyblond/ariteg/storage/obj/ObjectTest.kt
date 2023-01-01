package info.skyblond.ariteg.storage.obj

import info.skyblond.ariteg.storage.HashNotMatchException
import io.ipfs.multihash.Multihash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random
import kotlin.test.assertContentEquals

internal class ObjectTest {
    @Test
    fun testVerify() {
        val aritegObjects = listOf(
            Blob(ByteArray(10)),
            ListObject(emptyList()),
            TreeObject(emptyMap())
        )

        aritegObjects.forEach {
            val hash = it.hashString
            Assertions.assertDoesNotThrow { it.verify(hash) }
            Assertions.assertThrows(HashNotMatchException::class.java) { it.verify("hash") }
        }
    }

    @Test
    fun testLink() {
        val link = Link("hash", Link.Type.BLOB, 123)
        val encoded = link.encodedString()
        val restored = Link(encoded)
        println(link)
        println(encoded)
        println(restored)
        assertTrue { link == restored }
        assertTrue { link.hashCode() == restored.hashCode() }
        // same hash&type
        val link2 = Link("hash", Link.Type.BLOB, -1)
        assertTrue { link == link2 }
        assertTrue { link.hashCode() == link2.hashCode() }
        // different hash
        val link3 = Link("hash2", Link.Type.BLOB, 123)
        assertTrue { link != link3 }
        assertTrue { link.hashCode() != link3.hashCode() }
        // different type
        val link4 = Link("hash", Link.Type.LIST, 123)
        assertTrue { link != link4 }
        assertTrue { link.hashCode() != link4.hashCode() }
    }

    @Test
    fun testBlob() {
        val blob1 = Blob(ByteArray(10))
        val link1 = blob1.link
        assertEquals(blob1.hashString, link1.hash)
        assertEquals(10, link1.size)
        assertEquals(Link.Type.BLOB, link1.type)
        val blob2 = Blob(ByteArray(10))
        assertTrue(blob1 == blob2)
        assertTrue(blob1.hashCode() == blob2.hashCode())
        val blob3 = Blob(Random.nextBytes(10))
        assertTrue(blob1 != blob3)
        assertTrue(blob1.hashCode() != blob3.hashCode())
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    @Test
    fun testHash() {
        val samples = listOf(
            "Ariteg" to "3cd2e3c24733da354ccc574f016568bbcf2594a012c697a75803391cad9ad42f52506fd8eada5e7b3d5b0814d12a8a6b1b7d1c5e716c05228835587e0569d2a1",
            "hurui200320" to "f84d3838ffdd8c35c383dcca07daf0e7f1fe6abc01ce235567070f7bb4a117aed75f29f8c2c6945e060f2df2dba7240e64993f0c9e54c7e210c38b9f413e8b15",
            "" to "a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26",
        )

        samples.forEach { (value, expectHex) ->
            val hash = Multihash.fromBase58(Blob(value.encodeToByteArray()).hashString).hash
            val expect = expectHex.decodeHex()
            Assertions.assertArrayEquals(expect, hash)
        }
    }

    @Test
    fun testList() {
        val list1 = ListObject(
            listOf(
                Link("hash111", Link.Type.BLOB, 10),
                Link("hash222", Link.Type.BLOB, 10)
            )
        )
        val link1 = list1.link
        assertEquals(list1.hashString, link1.hash)
        assertEquals(list1.encoded.size, link1.size)
        assertEquals(Link.Type.LIST, link1.type)
        println(list1.encoded.decodeToString())
        val list2 = ListObject(list1.encoded)
        assertTrue(list1 == list2)
        assertTrue(list1.hashCode() == list2.hashCode())
        val list3 = ListObject(
            listOf(
                Link("hash222", Link.Type.BLOB, 10),
                Link("hash111", Link.Type.BLOB, 10)
            )
        )
        assertTrue(list1 != list3)
        assertTrue(list1.hashCode() != list3.hashCode())
    }

    @Test
    fun testTree() {
        val tree1 = TreeObject(
            mapOf(
                "111" to Link("hash111", Link.Type.BLOB, 10),
                "222" to Link("hash222", Link.Type.BLOB, 10)
            )
        )
        val link1 = tree1.link
        assertEquals(tree1.hashString, link1.hash)
        assertEquals(tree1.encoded.size, link1.size)
        assertEquals(Link.Type.TREE, link1.type)
        println(tree1.encoded.decodeToString())
        val tree2 = TreeObject(tree1.encoded)
        assertTrue(tree1 == tree2)
        assertTrue(tree1.hashCode() == tree2.hashCode())
        // different order, but same content
        val tree3 = TreeObject(
            mapOf(
                "222" to Link("hash222", Link.Type.BLOB, 10),
                "111" to Link("hash111", Link.Type.BLOB, 10)
            )
        )
        assertTrue(tree1 == tree3)
        assertTrue(tree1.hashCode() == tree3.hashCode())
        assertTrue(tree1.link == tree3.link)
        assertContentEquals(tree1.encoded, tree3.encoded)
        // different content
        val tree4 = TreeObject(
            mapOf(
                "2221" to Link("hash222", Link.Type.BLOB, 10),
                "111" to Link("hash111", Link.Type.BLOB, 10)
            )
        )
        assertTrue(tree1 != tree4)
        assertTrue(tree1.hashCode() != tree4.hashCode())
    }

    @Test
    fun testEntry() {
        val entry1 = Entry(
            "name", Link("hash", Link.Type.TREE, 123), ZonedDateTime.now()
        )
        println(entry1.encoded.decodeToString())
        val entry2 = Entry(entry1.encoded)
        assertTrue(entry1 == entry2)
        assertTrue(entry1.hashCode() == entry2.hashCode())
        // same name, different content
        val entry3 = Entry(
            "name", Link("hash111", Link.Type.BLOB, 123), ZonedDateTime.now()
        )
        assertTrue(entry1 == entry3)
        assertTrue(entry1.hashCode() == entry3.hashCode())
        // different name, same content
        val entry4 = Entry(
            "name111", Link("hash", Link.Type.TREE, 123), ZonedDateTime.now()
        )
        assertTrue(entry1 != entry4)
        assertTrue(entry1.hashCode() != entry4.hashCode())
    }
}
