package info.skyblond.ariteg
//
//import info.skyblond.ariteg.storage.layer.FileNativeStorageLayer
//import org.slf4j.LoggerFactory
//import java.io.File
//import java.security.MessageDigest
//
//
//object Main {
//    private val logger = LoggerFactory.getLogger("Application")
//
//    @JvmStatic
//    fun main(args: Array<String>) {
//        AsyncNativeStorageClient(File("./data").also { it.mkdirs() }).use { storageClient ->
//            val storageLayer = FileNativeStorageLayer(storageClient)
//            logger.info("Storage file")
//            val obj = timing {
//                File("C:\\Adobe_2021_MasterCol_win_v11.9#1_20210901.iso").inputStream().use {
//                    storageLayer.writeInputStreamToProto(it)
//                }
//            }
//            logger.info("Wait all writing finished")
//            timing {
//                while (!storageClient.allClear())
//                    Thread.yield()
//            }
//            logger.info("Read out, calculate sha256")
//            val digest = MessageDigest.getInstance("SHA-256")
//            // F84EEF7EB41B96598A42D0FA157487DB7F2F493157E2B592FF07F3431BB6B676
//            val buffer = ByteArray(4096 * 1024) // 4MB
//            val sha256 = timing {
//                storageLayer.readInputStreamFromProto(obj).use { inputStream ->
//                    var counter: Int
//                    while (inputStream.read(buffer, 0, buffer.size)
//                            .also { counter = it } != -1
//                    ) {
//                        digest.update(buffer, 0, counter)
//                    }
//                    digest.digest()
//                }
//            }
//            logger.info(sha256.toHex())
//        }
//    }
//
//    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
//
//    private fun <T> timing(foo: () -> T): T {
//        val start = System.currentTimeMillis()
//        val result = foo()
//        val end = System.currentTimeMillis()
//        logger.info("Time used: ${end - start} ms")
//        return result
//    }
//}
