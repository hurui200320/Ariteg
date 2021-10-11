package info.skyblond.ariteg.proto

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.proto.storage.ProtoStorageService
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.apache.ProxyConfiguration
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import kotlin.math.min
import kotlin.random.Random

fun simpleRead(
    storageService: ProtoStorageService, target: AritegLink
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

fun getProxyApacheClientBuilder(): ApacheHttpClient.Builder {
    return ApacheHttpClient.builder()
        .proxyConfiguration(
            ProxyConfiguration.builder()
                .endpoint(URI.create("http://127.0.0.1:1081"))
                .build()
        )
}

fun prepareTestFile(size: Long): File {
    val file = File.createTempFile(System.currentTimeMillis().toString(), System.nanoTime().toString())
    val buffer = ByteArray(4096) // 4KB
    file.outputStream().use { outputStream ->
        var counter = 0L
        while (counter < size) {
            Random.nextBytes(buffer)
            outputStream.write(buffer, 0, min(buffer.size.toLong(), size - counter).toInt())
            counter += buffer.size
        }
    }
    return file
}
