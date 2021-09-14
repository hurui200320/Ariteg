package info.skyblond.ariteg

import info.skyblond.ariteg.storage.disk.AritegDiskStorage
import info.skyblond.ariteg.storage.toMultihashBase58
import org.slf4j.LoggerFactory
import java.io.File


object Main {
    private val logger = LoggerFactory.getLogger("Application")

    @JvmStatic
    fun main(args: Array<String>) {
        AritegDiskStorage(File("./data").apply { mkdirs() }).use { diskStorage ->
            // SHA256 7z
            // 5831573504 字节 (5561 MiB)
            // 3D197AC3360B5047FA3525DB6E6F2DC467BCCF1FE684AEAE10BD89E1BB3319DB
//            val targetFile = File("D:\\Tixati\\zh-cn_windows_10_consumer_editions_version_21h1_updated_aug_2021_x64_dvd_4de56d76.iso")
//            logger.info("Storing file...")
//            val obj = diskStorage.writeInputStreamToProto(targetFile.inputStream())

            logger.info("Parsing link...")
            val link = diskStorage.parse("8tXs1qeEfPMqpzdsPtrruEm3oC1eM6DVvFjZXerQEsponEZKQp2mogYoENSekX2rx1eD25YuF1vfvsKEAUhHfZUVKd")!!
            val obj = diskStorage.fetchProtoBlocking(link)
            logger.info("Object type: " + obj.typeOfObject)
            logger.info("Object multihash: " + obj.toMultihashBase58())

            val loadedFile = File("./test.iso")
            logger.info("Restore file...")
            loadedFile.outputStream().use { fileOutputStream ->
                diskStorage.readInputStreamFromProto(obj).use { protoInputStream ->
                    protoInputStream.copyTo(fileOutputStream)
                }
            }

            logger.info("Done!")
        }
    }
}
