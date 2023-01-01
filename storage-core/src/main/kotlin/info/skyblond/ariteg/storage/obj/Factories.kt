package info.skyblond.ariteg.storage.obj

import com.dampcake.bencode.Bencode
import com.dampcake.bencode.Type
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun Blob(data: ByteArray): Blob = BlobImpl(data)
fun Link(hash: String, type: Link.Type, size: Int): Link = LinkImpl(hash, type, size)

internal fun Link(
    bencode: String
): Link {
    val map = Bencode(Charsets.UTF_8).decode(bencode.encodeToByteArray(), Type.DICTIONARY)
    return Link(
        hash = map["hash"] as String,
        type = Link.Type.valueOf(map["type"] as String),
        size = (map["size"] as Long).toInt()
    )
}

fun ListObject(content: List<Link>): ListObject = ListImpl(content)
fun ListObject(data: ByteArray): ListObject {
    val bencode = Bencode(Charsets.UTF_8)
    val map = bencode.decode(data, Type.DICTIONARY)
    val content = (map["content"] as List<*>).map { Link(it as String) }
    return ListImpl(content)
}

fun TreeObject(content: Map<String, Link>): TreeObject = TreeImpl(content.toSortedMap())
fun TreeObject(data: ByteArray): TreeObject {
    val bencode = Bencode(Charsets.UTF_8)
    val map = bencode.decode(data, Type.DICTIONARY)
    val content = (map["content"] as Map<*, *>)
        .mapKeys { it.key as String }
        .mapValues { Link(it.value as String) }
    return TreeImpl(content)
}

fun Entry(name: String, root: Link, ctime: ZonedDateTime): Entry = EntryImpl(name, root, ctime)
fun Entry(data: ByteArray): Entry {
    val bencode = Bencode(Charsets.UTF_8)
    val map = bencode.decode(data, Type.DICTIONARY)
    return EntryImpl(
        name = map["name"] as String,
        root = Link(map["root"] as String),
        ctime = ZonedDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(map["ctime"] as String))
    )
}
