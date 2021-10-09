package info.skyblond.ariteg.proto

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.proto.storage.FileProtoStorageService
import java.io.ByteArrayInputStream
import java.io.InputStream

fun simpleRead(
    storageService: FileProtoStorageService, target: AritegLink
): InputStream {
    val proto = storageService.loadProto(target)
    // Is blob, return the data as input stream
    if (proto.type == ObjectType.BLOB)
        return ByteArrayInputStream(proto.data.toByteArray())

    // Is list
    require(proto.type == ObjectType.LIST) { "Unsupported link type: ${proto.type}" }
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
                val link = linkList.removeAt(0)
                val obj = storageService.loadProto(link)
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
