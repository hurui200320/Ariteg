package info.skyblond.ariteg.file

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.multihash.MultihashSerializer
import info.skyblond.ariteg.objects.toMultihash
import io.ipfs.multihash.Multihash
import kotlinx.serialization.Serializable

/**
 * File meta service is similar to the proto meta service.
 * It stores the metadata of files.
 * */
interface FileIndexService : AutoCloseable {
    @Serializable
    data class Entry(
        val prefix: String,
        val name: String,
        @Serializable(with = MultihashSerializer::class)
        val multihash: Multihash,
    ) {
        companion object {
            fun createEntry(prefix: String, name: String, link: AritegLink): Entry =
                Entry(prefix, name, link.multihash.toMultihash())

            fun createEntry(prefix: String, name: String, base58Multihash: String): Entry =
                Entry(prefix, name, Multihash.fromBase58(base58Multihash))
        }
    }

    fun parseFromEntry(entry: Entry): AritegLink?

    fun saveEntry(entry: Entry): Boolean

    fun getEntry(prefix: String, name: String): Entry?

    fun listEntry(prefix: String): List<Entry>

    fun deleteEntry(prefix: String, name: String): Entry?

    fun moveEntry(oldPrefix: String, newPrefix: String, name: String): Boolean

    fun renameEntry(prefix: String, oldName: String, newName: String): Boolean
}
