package info.skyblond.ariteg.file

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.proto.meta.ProtoMetaService
import java.util.concurrent.ConcurrentHashMap

class InMemoryFileIndexService(
    private val protoMetaService: ProtoMetaService
) : FileIndexService {
    private val concurrentHashMap = ConcurrentHashMap<Pair<String, String>, FileIndexService.Entry>()

    fun dumpContent(): Map<Pair<String, String>, FileIndexService.Entry> = concurrentHashMap.toMap()

    override fun parseFromEntry(entry: FileIndexService.Entry): AritegLink? {
        return protoMetaService.getByPrimaryMultihash(entry.multihash)?.let {
            AritegLink.newBuilder()
                .setName(entry.name)
                .setType(it.type)
                .setMultihash(ByteString.copyFrom(it.primaryMultihash.toBytes()))
                .build()
        }
    }

    override fun saveEntry(entry: FileIndexService.Entry): Boolean {
        return concurrentHashMap.putIfAbsent(Pair(entry.prefix, entry.name), entry) == null
    }

    override fun getEntry(prefix: String, name: String): FileIndexService.Entry? {
        return concurrentHashMap[Pair(prefix, name)]
    }

    override fun listEntry(prefix: String): List<FileIndexService.Entry> {
        return concurrentHashMap.filter { it.key.first == prefix }.map { it.value }
    }

    override fun deleteEntry(prefix: String, name: String): FileIndexService.Entry? {
        return concurrentHashMap.remove(Pair(prefix, name))
    }

    override fun moveEntry(oldPrefix: String, newPrefix: String, name: String): Boolean {
        val old = concurrentHashMap.remove(Pair(oldPrefix, name)) ?: return false
        return concurrentHashMap.putIfAbsent(Pair(newPrefix, name), old.copy(prefix = newPrefix)) == null
    }

    override fun renameEntry(prefix: String, oldName: String, newName: String): Boolean {
        val old = concurrentHashMap.remove(Pair(prefix, oldName)) ?: return false
        return concurrentHashMap.putIfAbsent(Pair(prefix, newName), old.copy(name = newName)) == null
    }

    override fun close() = Unit
}
