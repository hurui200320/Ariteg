package info.skyblond.ariteg.storage

import info.skyblond.ariteg.storage.obj.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption
import java.util.*


class FileStorage(
    private val baseDir: File,
    override val key: ByteArray? = null
) : AbstractStorage<File>() {
    private val logger = KotlinLogging.logger("FileStorage")

    init {
        check(!baseDir.exists() || baseDir.isDirectory) { "Base dir exists but is not a directory: $baseDir" }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        if (key != null) {
            require(key.size == 32) { "The key must be 256bits" }
        }
    }

    override fun mapToPath(type: String, name: String): File {
        // divide into sub folders to ease the small file performance issue
        val typeDir = File(baseDir, type.lowercase()).also { it.mkdirs() }
        return if (type != "entry") {
            val slice = name.chunked(5)
            val parent = File(typeDir, slice.dropLast(1).joinToString(File.separator))
            while (true) {
                parent.mkdirs()
                if (parent.exists()) break
                logger.warn { "Waiting for ${parent.canonicalPath}" }
            }
            File(parent, slice.last() + ".dat")
        } else {
            File(typeDir, "$name.dat")
        }
    }

    override fun listEntryPath(): Sequence<File> = sequence {
        val queue = LinkedList(listOf(File(baseDir, "entry").also { it.mkdirs() }))

        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            check(f.isDirectory) { "Expect a dir but got a file: ${f.canonicalPath}" }
            val list = f.listFiles()?.asSequence() ?: emptySequence()
            list.forEach {
                if (it.isDirectory) {
                    queue.add(it)
                } else if (it.isFile && it.extension == "dat") {
                    yield(it)
                }
            }
        }
    }

    override fun listObjects(type: Link.Type): Sequence<Link> = sequence {
        val queue = LinkedList(listOf(File(baseDir, type.name.lowercase()).also { it.mkdirs() }))
        val basePath = baseDir.canonicalPath
        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            check(f.isDirectory) { "Expect a dir but got a file: ${f.canonicalPath}" }
            val list = f.listFiles()?.asSequence() ?: emptySequence()
            list.forEach {
                if (it.isDirectory) {
                    queue.add(it)
                } else if (it.isFile && it.extension == "dat") {
                    val fullPath = it.canonicalPath
                    // remove base path and `,dat` suffix
                    val cleaned = fullPath
                        .drop(basePath.length + type.name.length + 2)
                        .dropLast(4)
                        .replace(File.separator, "")
                    yield(Link(cleaned, type, -1))
                }
            }
        }
    }

    override fun close() {
        // nop
    }

    override suspend fun internalWrite(path: File, data: ByteArray) {
        try {
            withContext(Dispatchers.IO) {
                Files.newOutputStream(path.toPath(), StandardOpenOption.CREATE_NEW)
                    .use { it.write(data) }
            }
        } catch (e: FileAlreadyExistsException) {
            throw ObjectAlreadyExistsException(e)
        }
    }

    override suspend fun internalRead(path: File): ByteArray {
        return try {
            withContext(Dispatchers.IO) {
                Files.readAllBytes(path.toPath())
            }
        } catch (e: NoSuchFileException) {
            throw ObjectNotFoundException(e)
        }
    }

    override suspend fun internalDelete(path: File) {
        try {
            withContext(Dispatchers.IO) {
                Files.delete(path.toPath())
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to delete ${path.canonicalPath}" }
        }
    }
}
