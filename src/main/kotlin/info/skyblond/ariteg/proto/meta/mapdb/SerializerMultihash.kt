package info.skyblond.ariteg.proto.meta.mapdb

import io.ipfs.multihash.Multihash
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.serializer.GroupSerializer
import org.mapdb.serializer.SerializerByteArray
import java.io.IOException

class SerializerMultihash : GroupSerializer<Multihash> {
    private val serializer = SerializerByteArray()
    override fun valueArraySearch(keys: Any, key: Multihash): Int {
        return serializer.valueArraySearch(keys, key.toBytes())
    }

    override fun valueArraySearch(keys: Any, key: Multihash, comparator: Comparator<*>?): Int {
        return serializer.valueArraySearch(keys, key.toBytes(), comparator)
    }

    @Throws(IOException::class)
    override fun valueArraySerialize(out: DataOutput2, vals: Any) {
        serializer.valueArraySerialize(out, vals)
    }

    @Throws(IOException::class)
    override fun valueArrayDeserialize(`in`: DataInput2, size: Int): Any {
        return serializer.valueArrayDeserialize(`in`, size)
    }

    override fun valueArrayGet(vals: Any, pos: Int): Multihash {
        return Multihash.deserialize(serializer.valueArrayGet(vals, pos))
    }

    override fun valueArraySize(vals: Any): Int {
        return serializer.valueArraySize(vals)
    }

    override fun valueArrayEmpty(): Any {
        return serializer.valueArrayEmpty()
    }

    override fun valueArrayPut(vals: Any, pos: Int, newValue: Multihash): Any {
        return serializer.valueArrayPut(vals, pos, newValue.toBytes())
    }

    override fun valueArrayUpdateVal(vals: Any, pos: Int, newValue: Multihash): Any {
        return serializer.valueArrayUpdateVal(vals, pos, newValue.toBytes())
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
    override fun serialize(out: DataOutput2, value: Multihash) {
        serializer.serialize(out, value.toBytes())
    }

    @Throws(IOException::class)
    override fun deserialize(input: DataInput2, available: Int): Multihash {
        return Multihash.deserialize(serializer.deserialize(input, available))
    }
}
