package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture


class FileStorage(
    private val baseDir: File,
    override val key: ByteArray? = null
) : AbstractStorage() {
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

    private fun getFile(type: String, name: String): File {
        val dir = File(baseDir, type.lowercase())
        dir.mkdirs()
        return File(dir, name)
    }

    private fun internalWrite(type: Link.Type, obj: AritegObject): CompletableFuture<Link> = CompletableFuture.supplyAsync {
        val hash = obj.getHashString().get()
        val file = getFile(type.name, hash)

        FileUtils.openOutputStream(file).use {
            it.write(getData(obj.encodeToBytes()))
        }

        Link(hash, type)
    }

    override fun writeBlob(blob: Blob): CompletableFuture<Link> = internalWrite(Link.Type.BLOB, blob)

    override fun writeList(listObj: ListObject): CompletableFuture<Link> = internalWrite(Link.Type.LIST, listObj)

    override fun writeTree(treeObj: TreeObject): CompletableFuture<Link> = internalWrite(Link.Type.TREE, treeObj)

    private fun internalRead(link: Link): ByteArray {
        val file = getFile(link.type.name, link.hash)
        return FileUtils.openInputStream(file).use {
            parseData(it.readAllBytes())
        }
    }

    override fun readBlob(link: Link): CompletableFuture<Blob> = CompletableFuture.supplyAsync {
        val data = internalRead(link)
        Blob(data).also { it.verify(link.hash) }
    }

    override fun readList(link: Link): CompletableFuture<ListObject> = CompletableFuture.supplyAsync {
        val json = internalRead(link).decodeToString()
        ListObject.fromJson(json).also { it.verify(link.hash) }
    }

    override fun readTree(link: Link): CompletableFuture<TreeObject> = CompletableFuture.supplyAsync {
        val json = internalRead(link).decodeToString()
        TreeObject.fromJson(json).also { it.verify(link.hash) }
    }

    private fun internalDelete(link: Link) {
        val file = getFile(link.type.name, link.hash)
        if (!FileUtils.deleteQuietly(file)) {
            logger.warn { "Failed to delete ${file.canonicalPath}" }
        }
    }

    override fun deleteBlob(link: Link): CompletableFuture<Void> = CompletableFuture.runAsync {
        internalDelete(link)
    }

    override fun deleteList(link: Link): CompletableFuture<Void> = CompletableFuture.runAsync {
        internalDelete(link)
    }

    override fun deleteTree(link: Link): CompletableFuture<Void> = CompletableFuture.runAsync {
        internalDelete(link)
    }

    override fun listObjects(): CompletableFuture<Triple<Set<String>, Set<String>, Set<String>>> =
        CompletableFuture.supplyAsync {
            val blobList = getFile(Link.Type.BLOB.name, "something").parentFile.listFiles()?.map { it.name } ?: emptyList()
            val listList = getFile(Link.Type.LIST.name, "something").parentFile.listFiles()?.map { it.name } ?: emptyList()
            val treeList = getFile(Link.Type.TREE.name, "something").parentFile.listFiles()?.map { it.name } ?: emptyList()

            Triple(blobList.toSet(), listList.toSet(), treeList.toSet())
        }

    override fun addEntry(entry: Entry): CompletableFuture<Entry> = CompletableFuture.supplyAsync {
        val file = getFile("entry", base64Encode(entry.name))
        val data = getData(entry.toJson().encodeToByteArray())
        FileUtils.writeByteArrayToFile(file, data)
        entry
    }

    override fun removeEntry(entry: Entry): CompletableFuture<Void> = CompletableFuture.runAsync {
        val file = getFile("entry", base64Encode(entry.name))
        if (!FileUtils.deleteQuietly(file)) {
            logger.info { "Failed to delete file: ${file.canonicalPath}" }
        }
    }

    override fun listEntry(): Iterable<Entry> {
        val file = getFile("entry", "something").parentFile
        return object : Iterable<Entry> {
            val listOfFile = file.listFiles()?.filter { it.isFile }
                ?: error("Failed to list entries: invalid dir: ${file.canonicalPath}")

            override fun iterator(): Iterator<Entry> {
                return object : Iterator<Entry> {
                    private var pointer = 0

                    override fun hasNext(): Boolean = pointer < listOfFile.size

                    override fun next(): Entry {
                        if (!hasNext()) throw NoSuchElementException("No next elements")
                        val json = parseData(FileUtils.readFileToByteArray(listOfFile[pointer++])).decodeToString()
                        return Entry.fromJson(json)
                    }
                }
            }
        }
    }

    override fun close() {
        // nop
    }
}
