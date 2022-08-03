package info.skyblond.ariteg.storage

import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.io.File


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
        FileUtils.openOutputStream(path).use {
            it.write(data)
        }
    }

    override fun internalRead(path: File): ByteArray {
        return FileUtils.readFileToByteArray(path)
    }

    override fun internalDelete(path: File) {
        if (!FileUtils.deleteQuietly(path)) {
            logger.warn { "Failed to delete ${path.canonicalPath}" }
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
