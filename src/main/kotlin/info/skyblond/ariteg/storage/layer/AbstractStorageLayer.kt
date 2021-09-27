package info.skyblond.ariteg.storage.layer

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.storage.client.StorageClient
import java.io.InputStream

/**
 * There are some common implementations, which might not be the best, but shouldn't
 * harm too much if you just use it.
 * */
abstract class AbstractStorageLayer(
    protected val storageClient: StorageClient
) : StorageLayer {
    /**
     * This is a common write implementation.
     * */
    override fun writeInputStreamToProto(
        inputStream: InputStream,
        blobSize: Int,
        listLength: Int,
    ): AritegObject {
        val linkList = mutableListOf<AritegLink>()
        // save the last stored proto, returned as root node
        var lastProto: AritegObject
        inputStream.use { inputSteam ->
            var actualCount: Int
            // This will at least store 1 blob.
            // if the input stream is empty, then an empty blob is stored.
            do {
                val bytes = ByteArray(blobSize)
                actualCount = inputSteam.read(bytes)
                if (actualCount == -1) actualCount = 0
                val blobObject = AritegObject.newBuilder()
                    .setType(ObjectType.BLOB)
                    .setData(ByteString.copyFrom(bytes, 0, actualCount))
                    .build()
                // store on the fly
                lastProto = blobObject
                linkList.add(storageClient.storeProto(blobObject))
            } while (actualCount > 0)
        }

        var i = 0
        while (linkList.size > 1) {
            // merging blob list to list
            val list = List(minOf(listLength, linkList.size - i)) { linkList.removeAt(i) }
            val listObject = AritegObject.newBuilder()
                .setType(ObjectType.LIST)
                .addAllLinks(list)
                .build()
            lastProto = listObject
            linkList.add(i++, storageClient.storeProto(listObject))
            // reset i if we reach the end
            if (i >= linkList.size) i = 0
        }
        assert(storageClient.storeProto(lastProto).multihash == linkList[0].multihash)
        // The result is combine all link into one single root
        return lastProto
    }
}
