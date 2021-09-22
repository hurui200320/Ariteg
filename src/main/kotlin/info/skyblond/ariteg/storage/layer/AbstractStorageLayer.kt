package info.skyblond.ariteg.storage.layer

import com.google.protobuf.ByteString
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
        val blobList = mutableListOf<AritegObject>()
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
                blobList.add(blobObject)
            } while (actualCount > 0)
        }

        var i = 0
        while (blobList.size > 1) {
            // merging blob list to list
            val linkList = List(minOf(listLength, blobList.size - i)) { storageClient.storeProto(blobList.removeAt(i)) }
            val listObject = AritegObject.newBuilder()
                .setType(ObjectType.LIST)
                .addAllLinks(linkList)
                .build()
            blobList.add(i++, listObject)
            // reset i if we reach the end
            if (i >= blobList.size) i = 0
        }
        // The result is combine all link into one single root
        return blobList[0].also { storageClient.storeProto(it) }
    }
}
