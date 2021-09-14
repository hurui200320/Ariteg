package info.skyblond.ariteg.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.AritegObjectType
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

abstract class AritegAbstractStorage : AritegStorageInterface {

    override fun fetchProtoBlocking(link: AritegLink): AritegObject {
        require(protoExists(link)) { "Link not found: ${link.toMultihashBase58()}" }
        while (!protoAvailable(link)) {
            TimeUnit.SECONDS.sleep(1)
        }
        return loadProto(link)
    }

    override fun writeInputStreamToProto(
        inputStream: InputStream,
        blobSize: Int,
        listLength: Int,
    ): AritegObject {
        val blobList = mutableListOf<AritegLink>()
        inputStream.use { inputSteam ->
            while (inputSteam.available() > 0) {
                val bytes = ByteArray(blobSize)
                val actualCount = inputSteam.read(bytes)
                val blobObject = AritegObject.newBuilder()
                    .setTypeOfObject(AritegObjectType.BLOB)
                    .setData(ByteString.copyFrom(bytes, 0, actualCount))
                    .build()
                // TODO non-blocking?
                blobList.add(storeProto(blobObject))
            }
        }

        var i = 0
        while (blobList.size > 1) {
            // merging blob list to list
            val linkList = List(minOf(176, blobList.size - i)) { blobList.removeAt(i) }
            val listObject = AritegObject.newBuilder()
                .setTypeOfObject(AritegObjectType.LIST)
                .addAllLinks(linkList)
                .build()
            blobList.add(i++, storeProto(listObject))
            // reset i if we reach the end
            if (i >= blobList.size) i = 0
        }

        return blobList[0].let {
            require(protoExists(it)) { "Object uploaded but not available" }
            while (!protoAvailable(it))
                TimeUnit.SECONDS.sleep(1)
            loadProto(it)
        }
    }

    override fun readInputStreamFromProto(
        proto: AritegObject
    ): InputStream {
        // Is blob, return the data as input stream
        if (proto.typeOfObject == AritegObjectType.BLOB)
            return ByteArrayInputStream(proto.data.toByteArray())

        // Is list
        require(proto.typeOfObject == AritegObjectType.LIST) { "Unsupported object type: ${proto.typeOfObject}" }
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
                    if (obj.typeOfObject == AritegObjectType.BLOB) {
                        // is blob, use it
                        currentBlob = obj
                        pointer = 0
                        return
                    }
                    // else, is list, add them to the list and try again
                    require(obj.typeOfObject == AritegObjectType.LIST) { "Unsupported object type: ${proto.typeOfObject}" }
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
}
