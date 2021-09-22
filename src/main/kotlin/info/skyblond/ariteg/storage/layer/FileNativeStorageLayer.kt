package info.skyblond.ariteg.storage.layer

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.storage.ObjectNotFoundException
import info.skyblond.ariteg.storage.ObjectNotSupportedException
import info.skyblond.ariteg.storage.client.StorageClient
import info.skyblond.ariteg.storage.client.disk.AbstractNativeStorageClient
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * This storage layer handle files adn directories.
 * Store data native file system, using [AbstractNativeStorageClient].
 *
 * This is for proof of concept only. Since it's no need to do restore
 * and easy to validate. (The code here is assuming no prepare link is required.)
 * */
class FileNativeStorageLayer(
    storageClient: AbstractNativeStorageClient
) : AbstractStorageLayer(storageClient) {
    /**
     * This implementation just read, without any status check.
     * */
    override fun readInputStreamFromProto(
        proto: AritegObject
    ): InputStream {
        // Is blob, return the data as input stream
        if (proto.type == ObjectType.BLOB)
            return ByteArrayInputStream(proto.data.toByteArray())

        // Is list
        require(proto.type == ObjectType.LIST) { "Unsupported object type: ${proto.type}" }
        return object : InputStream() {
            val linkList = mutableListOf<AritegLink>()
            var currentBlob: AritegObject? = null
            var pointer = 0

            init {
                // the initial link list is from proto
                linkList.addAll(proto.linksList)
            }

            private fun fetchNextBlob() {
                while (linkList.isNotEmpty() && currentBlob == null) {
                    // fetch the first link
                    val obj = storageClient.loadProto(linkList.removeAt(0))
                    if (obj.type == ObjectType.BLOB) {
                        // is blob, use it
                        currentBlob = obj
                        pointer = 0
                        return
                    }
                    // else, is list, add them to the list and try again
                    require(obj.type == ObjectType.LIST) { "Unsupported object type: ${proto.type}" }
                    linkList.addAll(0, obj.linksList)
                }
            }

            override fun read(): Int {
                if (currentBlob == null) {
                    // refresh blob
                    fetchNextBlob()
                }
                if (currentBlob == null || currentBlob!!.data.size() == 0) {
                    // really the end
                    return -1
                }
                // we got the blob, read the value
                val result = currentBlob!!.data.byteAt(pointer++).toUByte().toInt()
                require(result != -1) { "Unexpected EOF" }
                if (pointer >= currentBlob!!.data.size()) {
                    // if we are the end of blob, release it
                    currentBlob = null
                }
                return result
            }
        }
    }

    override fun storeDir(dir: File): AritegObject {
        require(dir.isDirectory) { "$dir is not dir." }
        val obj = AritegObject.newBuilder()
            .setType(ObjectType.TREE)
            .addAllLinks(dir.listFiles()!!.map {
                if (it.isDirectory) {
                    storageClient.storeProto(it.name, storeDir(it))
                } else if (it.isFile) {
                    storageClient.storeProto(it.name, writeInputStreamToProto(it.inputStream()))
                } else {
                    error("$it is not dir nor file.")
                }
            })
            .build()
        storageClient.storeProto(obj)
        return obj
    }

    override fun walkTree(treeProto: AritegObject, foo: (StorageClient, StorageLayer, String, AritegObject) -> Unit) {
        walkTreeHelper(treeProto, "/", foo)
    }

    private fun walkTreeHelper(
        treeProto: AritegObject,
        parent: String,
        foo: (StorageClient, StorageLayer, String, AritegObject) -> Unit
    ) {
        if (treeProto.type != ObjectType.TREE) throw ObjectNotSupportedException("Expect tree but ${treeProto.type} is given")
        treeProto.linksList.forEach { link ->
            if (!storageClient.linkExists(link)) throw ObjectNotFoundException(link)
            while (!storageClient.linkAvailable(link))
                TimeUnit.SECONDS.sleep(1)
            val obj = storageClient.loadProto(link)
            when (obj.type) {
                ObjectType.BLOB, ObjectType.LIST -> foo(storageClient, this, parent + link.name, obj)
                ObjectType.TREE -> walkTreeHelper(obj, parent + link.name + "/", foo)
                else -> throw ObjectNotSupportedException(obj.type.name)
            }
        }
    }
}
