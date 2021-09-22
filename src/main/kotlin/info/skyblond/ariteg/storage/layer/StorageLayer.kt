package info.skyblond.ariteg.storage.layer

import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.storage.client.StorageClient
import java.io.File
import java.io.InputStream

/**
 * A storage layer is high level API facing files and directories, or objects.
 * While the low level clients are used to handle I/O read and write.
 *
 * Thus, you can have the different layer to handle different types of file system,
 * like file-based, or object based, etc., and store them on different underlying
 * system, like native filesystem, or AWS S3, etc.
 * */
interface StorageLayer {
    companion object {
        /**
         * Default BlobObject data size when splitting files.
         * */
        const val DEFAULT_BLOB_SIZE = 256 * 1024

        /**
         * Default branch size when combining links into ListObject.
         * */
        const val DEFAULT_LIST_LENGTH = 176
    }

    /**
     * Save a inputStream to the storage and return the root object.
     * Will close the inputStream for you.
     *
     * If the total size smaller than blobSize, then BlobObject is returned.
     * Otherwise, a ListObject is returned.
     * */
    fun writeInputStreamToProto(
        inputStream: InputStream,
        blobSize: Int = DEFAULT_BLOB_SIZE,
        listLength: Int = DEFAULT_LIST_LENGTH
    ): AritegObject

    /**
     * Return a inputStream representing a stream written by [writeInputStreamToProto].
     * Only BlobObject and ListObject is allowed.
     * Note: It's storage layer's job to provide someway to restore or check if
     * the whole merkel tree is available for read.
     * */
    fun readInputStreamFromProto(
        proto: AritegObject
    ): InputStream

    /**
     * Store a dir and return a TreeObject.
     * */
    // TODO This is dir, but not all storage layers are file/dir based.
    fun storeDir(dir: File): AritegObject

    /**
     * Walk the tree, call [foo] on each blob or list entry.
     * String is the path of the entry, start with "/"
     */
    fun walkTree(treeProto: AritegObject, foo: (StorageClient, StorageLayer, String, AritegObject) -> Unit)
}
