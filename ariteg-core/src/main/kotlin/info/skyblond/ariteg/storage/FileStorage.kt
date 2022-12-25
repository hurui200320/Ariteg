package info.skyblond.ariteg.storage

import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption


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
        val dir = File(baseDir, type.lowercase())
        dir.mkdirs()
        return File(dir, name)
    }

    override fun internalWrite(path: File, data: ByteArray) {
        if (path.exists()) {
            throw ObjectAlreadyExistsException()
        }
        Files.newOutputStream(path.toPath(), StandardOpenOption.CREATE).use {
            it.write(data)
        }
    }

    override fun internalRead(path: File): ByteArray {
        return Files.readAllBytes(path.toPath())
    }

    override fun internalDelete(path: File) {
        try {
            Files.delete(path.toPath())
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to delete ${path.canonicalPath}" }
        }
    }

    override fun getParentPath(path: File): File {
        return path.parentFile
    }

    override fun listHashInPath(parentPath: File): Set<String> {
        return listPathInPath(parentPath).map { it.name }.toHashSet()
    }

    override fun listPathInPath(parentPath: File): List<File> {
        return parentPath.listFiles()?.filter { it.isFile }
            ?: error("Failed to list entries: invalid dir: ${parentPath.canonicalPath}")
    }
}
