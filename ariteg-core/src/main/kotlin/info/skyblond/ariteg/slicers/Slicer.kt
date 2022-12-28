package info.skyblond.ariteg.slicers

import info.skyblond.ariteg.storage.obj.Blob
import java.io.InputStream

interface Slicer {

    /**
     * Slice the [input] and return a [Sequence] (backed with a generator).
     * Note: use [java.nio.file.Files.newInputStream] to get the best performance.
     * The returned [Sequence] is not thread-safe.
     * */
    fun slice(input: InputStream): Sequence<Blob>
}
