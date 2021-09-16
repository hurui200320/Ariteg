package info.skyblond.ariteg.storage.disk;

import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializer;
import org.mapdb.serializer.SerializerByteArray;

import java.io.IOException;
import java.util.Comparator;

public class SerializerByteString implements GroupSerializer<ByteString> {
    private final SerializerByteArray serializer = new SerializerByteArray();

    @Override
    public int valueArraySearch(Object keys, ByteString key) {
        return this.serializer.valueArraySearch(keys, key.toByteArray());
    }

    @Override
    public int valueArraySearch(Object keys, ByteString key, Comparator comparator) {
        return this.serializer.valueArraySearch(keys, key.toByteArray(), comparator);
    }

    @Override
    public void valueArraySerialize(DataOutput2 out, Object vals) throws IOException {
        this.serializer.valueArraySerialize(out, vals);
    }

    @Override
    public Object valueArrayDeserialize(DataInput2 in, int size) throws IOException {
        return this.serializer.valueArrayDeserialize(in, size);
    }

    @Override
    public ByteString valueArrayGet(Object vals, int pos) {
        return ByteString.copyFrom(this.serializer.valueArrayGet(vals, pos));
    }

    @Override
    public int valueArraySize(Object vals) {
        return this.serializer.valueArraySize(vals);
    }

    @Override
    public Object valueArrayEmpty() {
        return this.serializer.valueArrayEmpty();
    }

    @Override
    public Object valueArrayPut(Object vals, int pos, ByteString newValue) {
        return this.serializer.valueArrayPut(vals, pos, newValue.toByteArray());
    }

    @Override
    public Object valueArrayUpdateVal(Object vals, int pos, ByteString newValue) {
        return this.serializer.valueArrayUpdateVal(vals, pos, newValue.toByteArray());
    }

    @Override
    public Object valueArrayFromArray(Object[] objects) {
        return this.serializer.valueArrayFromArray(objects);
    }

    @Override
    public Object valueArrayCopyOfRange(Object vals, int from, int to) {
        return this.serializer.valueArrayCopyOfRange(vals, from, to);
    }

    @Override
    public Object valueArrayDeleteValue(Object vals, int pos) {
        return this.serializer.valueArrayDeleteValue(vals, pos);
    }

    @Override
    public void serialize(@NotNull DataOutput2 out, @NotNull ByteString value) throws IOException {
        this.serializer.serialize(out, value.toByteArray());
    }

    @Override
    public ByteString deserialize(@NotNull DataInput2 input, int available) throws IOException {
        return ByteString.copyFrom(this.serializer.deserialize(input, available));
    }
}
