package info.skyblond.ariteg.storage.obj

import com.dampcake.bencode.Bencode
import info.skyblond.ariteg.storage.HashNotMatchException
import io.ipfs.multihash.Multihash
import org.bouncycastle.crypto.digests.SHA3Digest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

internal abstract class AbstractAritegObject : AritegObject {

    /**
     * Get the hash of the obj.
     * The objs are immutable, thus the hash is immutable.
     * */
    final override val hashString: String by lazy { calculateHash(encoded) }

    abstract val objType: Link.Type

    final override val link: Link by lazy { Link(hashString, objType, encoded.size) }

    /**
     * Test if the obj's hash matches the [expected] one.
     * */
    @Throws(HashNotMatchException::class)
    final override fun verify(expected: String) {
        if (hashString != expected) {
            throw HashNotMatchException(expected, hashString)
        }
    }

    private fun calculateHash(data: ByteArray): String {
        val digest = SHA3Digest(512)
        val output = ByteArray(digest.digestSize)
        digest.update(data, 0, data.size)
        digest.doFinal(output, 0)
        return Multihash(Multihash.Type.blake2b_512, output).toBase58()
    }

    protected fun encodeMap(map: Map<String, Any?>): ByteArray {
        val bencode = Bencode(Charsets.UTF_8)
        return bencode.encode(map)
    }
}

internal data class LinkImpl(
    override val hash: String,
    override val type: Link.Type,
    override val size: Int,
) : Link {
    override val encoded: ByteArray by lazy {
        val bencode = Bencode(Charsets.UTF_8)
        bencode.encode(mapOf("hash" to hash, "type" to type, "size" to size))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Link) return false

        if (hash != other.hash) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

internal fun Link.encodedString(): String = this.encoded.decodeToString()

internal data class BlobImpl(
    override val data: ByteArray
) : AbstractAritegObject(), Blob {
    override val objType: Link.Type
        get() = Link.Type.BLOB
    override val encoded: ByteArray
        get() = data

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Blob) return false

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

internal data class ListImpl(
    override val content: List<Link>
) : AbstractAritegObject(), ListObject {
    override val objType: Link.Type
        get() = Link.Type.LIST

    override val encoded: ByteArray by lazy {
        encodeMap(mapOf("content" to content.map { it.encodedString() }))
    }
}

internal data class TreeImpl(
    override val content: Map<String, Link>
) : AbstractAritegObject(), TreeObject {
    override val objType: Link.Type
        get() = Link.Type.TREE

    override val encoded: ByteArray by lazy {
        encodeMap(mapOf("content" to content.mapValues { it.value.encodedString() }))
    }
}

internal data class EntryImpl(
    override val name: String, override val root: Link, override val ctime: ZonedDateTime
) : Entry {
    override fun withName(newName: String): Entry = this.copy(name = newName)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntryImpl) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override val encoded: ByteArray by lazy {
        val bencode = Bencode(Charsets.UTF_8)
        bencode.encode(
            mapOf(
                "name" to name,
                "root" to root.encodedString(),
                "ctime" to DateTimeFormatter.ISO_DATE_TIME.format(ctime)
            )
        )
    }


}
