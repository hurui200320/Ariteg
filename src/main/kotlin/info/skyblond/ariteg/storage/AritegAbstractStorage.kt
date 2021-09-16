package info.skyblond.ariteg.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.AritegObjectType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

abstract class AritegAbstractStorage : AritegStorageInterface {

    override fun fetchProtoBlocking(link: AritegLink): AritegObject {
        require(linkExists(link)) { "Link not found: ${link.toMultihashBase58()}" }
        while (!linkAvailable(link)) {
            TimeUnit.SECONDS.sleep(1)
        }
        return loadProto(link)
    }

    override fun writeInputStreamToProto(
        inputStream: InputStream,
        blobSize: Int,
        listLength: Int,
    ): AritegObject {
        val blobList = mutableListOf<AritegObject>()
        inputStream.use { inputSteam ->
            while (inputSteam.available() > 0) {
                val bytes = ByteArray(blobSize)
                val actualCount = inputSteam.read(bytes)
                val blobObject = AritegObject.newBuilder()
                    .setType(AritegObjectType.BLOB)
                    .setData(ByteString.copyFrom(bytes, 0, actualCount))
                    .build()
                blobList.add(blobObject)
            }
        }

        if (blobList.isEmpty()) {
            // input stream get 0 byte, store a empty blob
            blobList.add(
                AritegObject.newBuilder()
                    .setType(AritegObjectType.BLOB)
                    .setData(ByteString.EMPTY)
                    .build()
            )
        }

        var i = 0
        while (blobList.size > 1) {
            // merging blob list to list
            val linkList = List(minOf(176, blobList.size - i)) { storeProto(blobList.removeAt(i)) }
            val listObject = AritegObject.newBuilder()
                .setType(AritegObjectType.LIST)
                .addAllLinks(linkList)
                .build()
            blobList.add(i++, listObject)
            // reset i if we reach the end
            if (i >= blobList.size) i = 0
        }
        return blobList[0].also { storeProto(it) }
    }

    override fun readInputStreamFromProto(
        proto: AritegObject
    ): InputStream {
        // Is blob, return the data as input stream
        if (proto.type == AritegObjectType.BLOB)
            return ByteArrayInputStream(proto.data.toByteArray())

        // Is list
        require(proto.type == AritegObjectType.LIST) { "Unsupported object type: ${proto.type}" }
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
                    val obj = loadProto(linkList.removeAt(0))
                    if (obj.type == AritegObjectType.BLOB) {
                        // is blob, use it
                        currentBlob = obj
                        pointer = 0
                        return
                    }
                    // else, is list, add them to the list and try again
                    require(obj.type == AritegObjectType.LIST) { "Unsupported object type: ${proto.type}" }
                    linkList.addAll(0, obj.linksList)
                }
            }

            override fun read(): Int {
                if (currentBlob == null) {
                    // refresh blob
                    fetchNextBlob()
                }
                if (currentBlob == null) {
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
            .setType(AritegObjectType.TREE)
            .addAllLinks(dir.listFiles()!!.map {
                if (it.isDirectory) {
                    storeProto(it.name, storeDir(it))
                } else if (it.isFile) {
                    storeProto(it.name, writeInputStreamToProto(it.inputStream()))
                } else {
                    error("$it is not dir nor file.")
                }
            })
            .build()
        storeProto(obj)
        return obj
    }

    override fun walkTree(treeProto: AritegObject, foo: (String, InputStream) -> Unit) {
        walkTreeHelper(treeProto, "/", foo)
    }

    private fun walkTreeHelper(treeProto: AritegObject, parent: String, foo: (String, InputStream) -> Unit) {
        if (treeProto.type != AritegObjectType.TREE) throw ObjectNotSupportedException("Expect tree but ${treeProto.type} is given")
        treeProto.linksList.forEach { link ->
            if (!linkExists(link)) throw ObjectNotFoundException(
                "Link missing: ${
                    link.multihash.toMultihash().toBase58()
                }"
            )
            while (!linkAvailable(link))
                TimeUnit.SECONDS.sleep(1)
            val obj = loadProto(link)
            when (obj.type) {
                AritegObjectType.BLOB, AritegObjectType.LIST -> foo(parent + link.name, readInputStreamFromProto(obj))
                AritegObjectType.TREE -> walkTreeHelper(obj, parent + link.name + "/", foo)
                else -> throw ObjectNotSupportedException(obj.type.name)
            }
        }
    }
}
