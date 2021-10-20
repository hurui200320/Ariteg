package info.skyblond.ariteg.file

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.proto.ProtoWriteService
import java.io.File
import java.util.concurrent.CompletableFuture

abstract class FileWriteService(
    protected val metaService: FileIndexService,
    protected val protoWriteService: ProtoWriteService,
    protected val blobSize: Int,
    protected val listSize: Int
) {
    fun write(prefix: String, name: String, file: File): FileIndexService.Entry? {
        val result = if (file.isDirectory)
            writeFolder(prefix, name, file)
        else if (file.isFile)
            writeFile(prefix, name, file)
        else
            error("$file is not dir nor file")
        // get false means entry with same path is already exists
        return if (metaService.saveEntry(result)) result else null

    }

    protected fun writeFile(prefix: String, name: String, file: File): FileIndexService.Entry {
        require(file.isDirectory) { "$file is not a file" }
        val (link, future) = file.inputStream().use {
            protoWriteService.writeChunk("", it, blobSize, listSize)
        }
        // wait all write is done
        future.get()
        return FileIndexService.Entry.createEntry(prefix, name, link)
    }

    protected fun storeDir(dir: File): Pair<AritegObject, CompletableFuture<Void>> {
        val linksAndFutures: List<Pair<AritegLink, CompletableFuture<Void>>> =
            dir.listFiles()!!.map { f ->
                if (f.isDirectory) {
                    storeDir(f).let { (proto, future1) ->
                        val (link, future2) = protoWriteService.writeProto(f.name, proto)
                        link to CompletableFuture.allOf(future1, future2)
                    }
                } else if (f.isFile) {
                    f.inputStream().use { ins ->
                        protoWriteService.writeChunk(f.name, ins, blobSize, listSize)
                    }
                } else {
                    error("$f is not a file nor a directory")
                }
            }

        val treeObj = AritegObject.newBuilder()
            .setType(ObjectType.TREE)
            .addAllLinks(linksAndFutures.map { it.first })
            .build()
        return treeObj to CompletableFuture.allOf(*linksAndFutures.map { it.second }.toTypedArray())
    }

    protected fun writeFolder(prefix: String, name: String, folder: File): FileIndexService.Entry {
        require(folder.isDirectory) { "$folder is not a folder." }
        val (obj, future1) = storeDir(folder)
        val (link, future2) = protoWriteService.writeProto("", obj)
        CompletableFuture.allOf(future1, future2).get()
        return FileIndexService.Entry.createEntry(prefix, name, link)
    }
}
