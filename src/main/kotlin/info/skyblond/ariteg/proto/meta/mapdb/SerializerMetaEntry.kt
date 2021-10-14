package info.skyblond.ariteg.proto.meta.mapdb

import info.skyblond.ariteg.proto.meta.ProtoMetaService
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.serializer.GroupSerializer
import org.mapdb.serializer.SerializerString
import java.io.IOException

/**
 * MapDB serializer for [ProtoMetaService.Entry].
 *
 * A simple workaround is converting [ProtoMetaService.Entry] to Json [String],
 * then delegate the logic to [SerializerString].
 * */
class SerializerMetaEntry : GroupSerializer<ProtoMetaService.Entry> {
    private val serializer = SerializerString()
    override fun valueArraySearch(keys: Any, key: ProtoMetaService.Entry): Int {
        return serializer.valueArraySearch(keys, Json.encodeToString(key))
    }

    override fun valueArraySearch(keys: Any, key: ProtoMetaService.Entry, comparator: Comparator<*>?): Int {
        return serializer.valueArraySearch(keys, Json.encodeToString(key), comparator)
    }

    @Throws(IOException::class)
    override fun valueArraySerialize(out: DataOutput2, vals: Any) {
        serializer.valueArraySerialize(out, vals)
    }

    @Throws(IOException::class)
    override fun valueArrayDeserialize(`in`: DataInput2, size: Int): Any {
        return serializer.valueArrayDeserialize(`in`, size)
    }

    override fun valueArrayGet(vals: Any, pos: Int): ProtoMetaService.Entry {
        return Json.decodeFromString(serializer.valueArrayGet(vals, pos))
    }

    override fun valueArraySize(vals: Any): Int {
        return serializer.valueArraySize(vals)
    }

    override fun valueArrayEmpty(): Any {
        return serializer.valueArrayEmpty()
    }

    override fun valueArrayPut(vals: Any, pos: Int, newValue: ProtoMetaService.Entry): Any {
        return serializer.valueArrayPut(vals, pos, Json.encodeToString(newValue))
    }

    override fun valueArrayUpdateVal(vals: Any, pos: Int, newValue: ProtoMetaService.Entry): Any {
        return serializer.valueArrayUpdateVal(vals, pos, Json.encodeToString(newValue))
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
    override fun serialize(out: DataOutput2, value: ProtoMetaService.Entry) {
        serializer.serialize(out, Json.encodeToString(value))
    }

    @Throws(IOException::class)
    override fun deserialize(input: DataInput2, available: Int): ProtoMetaService.Entry {
        return Json.decodeFromString(serializer.deserialize(input, available))
    }

}
