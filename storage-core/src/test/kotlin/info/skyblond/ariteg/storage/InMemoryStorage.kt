package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.Link
import java.util.concurrent.ConcurrentHashMap

class InMemoryStorage(
    override val key: ByteArray?
) : AbstractStorage<String>() {
    private val repo = ConcurrentHashMap<String, ByteArray>()

    override fun mapToPath(type: String, name: String): String =
        "${type.lowercase()}\\$name"

    override fun listEntryPath(): Sequence<String> = repo.asSequence()
        .map { it.key }.filter { it.startsWith("entry") }

    override fun listObjects(type: Link.Type): Sequence<Link> = repo.asSequence()
        .map { it.key }.filter { it.startsWith(type.name.lowercase()) }
        .map { Link(it.substringAfterLast("\\"), type, -1) }

    override fun close() {
        repo.clear()
    }

    override suspend fun internalDelete(path: String) {
        println("Delete $path")
        repo.remove(path)
    }

    override suspend fun internalRead(path: String): ByteArray {
        println("Read $path")
        return repo[path] ?: throw ObjectNotFoundException(NullPointerException())
    }

    override suspend fun internalWrite(path: String, data: ByteArray) {
        println("Write $path")
        repo.compute(path) { _, v ->
            if (v != null) throw ObjectAlreadyExistsException(null)
            else data
        }
    }
}
