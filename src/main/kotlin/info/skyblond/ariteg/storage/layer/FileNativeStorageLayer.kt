package info.skyblond.ariteg.storage.layer
//
//import info.skyblond.ariteg.AritegLink
//import info.skyblond.ariteg.AritegObject
//import info.skyblond.ariteg.ObjectType
//import info.skyblond.ariteg.storage.ObjectNotFoundException
//import info.skyblond.ariteg.storage.ObjectNotSupportedException
//import info.skyblond.ariteg.storage.client.StorageClient
//import info.skyblond.ariteg.storage.client.disk.AbstractNativeStorageClient
//import java.io.ByteArrayInputStream
//import java.io.File
//import java.io.InputStream
//import java.util.concurrent.TimeUnit
//
///**
// * This storage layer handle files adn directories.
// * Store data native file system, using [AbstractNativeStorageClient].
// *
// * This is for proof of concept only. Since it's no need to do restore
// * and easy to validate. (The code here is assuming no prepare link is required.)
// * */
//class FileNativeStorageLayer(
//    storageClient: AbstractNativeStorageClient
//) : AbstractStorageLayer(storageClient) {
//    /**
//     * This implementation just read, without any status check.
//     * */
//
//
//    fun storeDir(dir: File): AritegObject {
//        require(dir.isDirectory) { "$dir is not dir." }
//        val obj = AritegObject.newBuilder()
//            .setType(ObjectType.TREE)
//            .addAllLinks(dir.listFiles()!!.map {
//                if (it.isDirectory) {
//                    storageClient.storeProto(it.name, storeDir(it))
//                } else if (it.isFile) {
//                    storageClient.storeProto(it.name, writeInputStreamToProto(it.inputStream()))
//                } else {
//                    error("$it is not dir nor file.")
//                }
//            })
//            .build()
//        storageClient.storeProto(obj)
//        return obj
//    }
//
//    fun walkTree(treeProto: AritegObject, foo: (StorageClient, StorageLayer, String, AritegObject) -> Unit) {
//        walkTreeHelper(treeProto, "/", foo)
//    }
//
//    private fun walkTreeHelper(
//        treeProto: AritegObject,
//        parent: String,
//        foo: (StorageClient, StorageLayer, String, AritegObject) -> Unit
//    ) {
//        if (treeProto.type != ObjectType.TREE) throw ObjectNotSupportedException("Expect tree but ${treeProto.type} is given")
//        treeProto.linksList.forEach { link ->
//            if (!storageClient.linkExists(link)) throw ObjectNotFoundException(link)
//            while (!storageClient.linkAvailable(link))
//                TimeUnit.SECONDS.sleep(1)
//            while (!storageClient.linkAvailable(link)) Thread.yield()
//            val obj = storageClient.loadProto(link)
//            when (obj.type) {
//                ObjectType.BLOB, ObjectType.LIST -> foo(storageClient, this, parent + link.name, obj)
//                ObjectType.TREE -> walkTreeHelper(obj, parent + link.name + "/", foo)
//                else -> throw ObjectNotSupportedException(obj.type.name)
//            }
//        }
//    }
//}
// /**
//     * Parse a Base58 multihash [String] into a [AritegLink].
//     *
//     * This is designed for clients that need perform additional check,
//     * or some other actions when user querying a base58 multihash.
//     *
//     * Can throw exceptions something goes wrong. Also, it can return null
//     * if the client want to ensure all links returned is valid.
//     *
//     * The default implementation just decode and feed the multihash into Ariteg link.
//     * No further check is performed.
//     * */
//    fun parse(multihashString: String): AritegLink? {
//        val bytestring = ByteString.copyFrom(Multihash.fromBase58(multihashString).toBytes())
//        return AritegLink.newBuilder()
//            .setMultihash(bytestring)
//            .build()
//    }
//fun AritegObject.toMultihashBase58(): String {
//    val rawBytes = this.toByteArray()
//    val multihash = doSHA3Multihash512(rawBytes)
//    return multihash.toBase58()
//}
