package info.skyblond.ariteg.storage.obj

import info.skyblond.ariteg.storage.HashNotMatchException
import java.time.ZonedDateTime
import java.util.*

interface Writable {
    /**
     * Get the encoded form.
     * The objs are immutable, thus the encoded bytes are immutable.
     * */
    val encoded: ByteArray
}

interface AritegObject : Writable {
    /**
     * Get the hash of the obj.
     * The objs are immutable, thus the hash is immutable.
     * */
    val hashString: String

    /**
     * The link point to this obj.
     * */
    val link: Link

    /**
     * Test if the obj's hash matches the [expected] one.
     * */
    @Throws(HashNotMatchException::class)
    fun verify(expected: String)
}

interface Link : Writable {
    /**
     * The hash of the node
     * */
    val hash: String

    /**
     * Type of the node
     * */
    val type: Type

    /**
     * Size of the node. Data size (before encryption).
     * */
    val size: Int

    enum class Type {
        BLOB, LIST, TREE
    }
}

interface Blob : AritegObject {
    val data: ByteArray
}

interface ListObject : AritegObject {
    /**
     * Content. Element must be list or blob.
     * */
    val content: List<Link>
}

interface TreeObject : AritegObject {
    /**
     * Content.
     * Each name is an entry, point to a blob, list or tree.
     * */
    val content: Map<String, Link>
}

interface Entry: Writable {
    /**
     * Name of the entry.
     * */
    val name: String
    /**
     * The root link of the entry.
     * */
    val root: Link
    /**
     * create time.
     * */
    val ctime: ZonedDateTime

    /**
     * Return the same entry but different name
     * */
    fun withName(newName: String): Entry
}
