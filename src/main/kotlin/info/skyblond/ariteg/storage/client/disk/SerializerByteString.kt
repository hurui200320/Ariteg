package info.skyblond.ariteg.storage.client.disk

import org.mapdb.serializer.GroupSerializer
import com.google.protobuf.ByteString
import org.mapdb.serializer.SerializerByteArray
import kotlin.Throws
import java.io.IOException
import org.mapdb.DataOutput2
import org.mapdb.DataInput2
import java.util.Comparator

class SerializerByteString : GroupSerializer<ByteString> {
    private val serializer = SerializerByteArray()
    override fun valueArraySearch(keys: Any, key: ByteString): Int {
        return serializer.valueArraySearch(keys, key.toByteArray())
    }

    override fun valueArraySearch(keys: Any, key: ByteString, comparator: Comparator<*>?): Int {
        return serializer.valueArraySearch(keys, key.toByteArray(), comparator)
    }

    @Throws(IOException::class)
    override fun valueArraySerialize(out: DataOutput2, vals: Any) {
        serializer.valueArraySerialize(out, vals)
    }

    @Throws(IOException::class)
    override fun valueArrayDeserialize(`in`: DataInput2, size: Int): Any {
        return serializer.valueArrayDeserialize(`in`, size)
    }

    override fun valueArrayGet(vals: Any, pos: Int): ByteString {
        return ByteString.copyFrom(serializer.valueArrayGet(vals, pos))
    }

    override fun valueArraySize(vals: Any): Int {
        return serializer.valueArraySize(vals)
    }

    override fun valueArrayEmpty(): Any {
        return serializer.valueArrayEmpty()
    }

    override fun valueArrayPut(vals: Any, pos: Int, newValue: ByteString): Any {
        return serializer.valueArrayPut(vals, pos, newValue.toByteArray())
    }

    override fun valueArrayUpdateVal(vals: Any, pos: Int, newValue: ByteString): Any {
        return serializer.valueArrayUpdateVal(vals, pos, newValue.toByteArray())
    }

    override fun valueArrayFromArray(objects: Array<Any>): Any {
        return serializer.valueArrayFromArray(objects)
    }

    override fun valueArrayCopyOfRange(vals: Any, from: Int, to: Int): Any {
        return serializer.valueArrayCopyOfRange(vals, from, to)
    }

    override fun valueArrayDeleteValue(vals: Any, pos: Int): Any {
        return serializer.valueArrayDeleteValue(vals, pos)
    }

    @Throws(IOException::class)
    override fun serialize(out: DataOutput2, value: ByteString) {
        serializer.serialize(out, value.toByteArray())
    }

    @Throws(IOException::class)
    override fun deserialize(input: DataInput2, available: Int): ByteString {
        return ByteString.copyFrom(serializer.deserialize(input, available))
    }
}
