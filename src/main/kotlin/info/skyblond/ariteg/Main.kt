package info.skyblond.ariteg

import info.skyblond.ariteg.storage.calculateSha3
import info.skyblond.ariteg.storage.disk.AritegDiskAsyncStorage
import org.slf4j.LoggerFactory
import java.io.File


object Main {
    private val logger = LoggerFactory.getLogger("Application")

    @JvmStatic
    fun main(args: Array<String>) {
        val baseDir = File("D:\\MultiMC\\instances")
        AritegDiskAsyncStorage(File("./data").apply { mkdirs() }).use { diskStorage ->
            val obj = diskStorage.storeDir(baseDir)
            println("Stored finished!")
            diskStorage.walkTree(obj) { path, inputStream ->
                println(path)
                val targetFile = File(baseDir, path.removePrefix("/"))
                val loadedHash = inputStream.use { calculateSha3(it, 4096 * 1024) }
                println(loadedHash.toBase58())
                println(targetFile)
                val targetHash = targetFile.inputStream().use { calculateSha3(it, 4096 * 1024) }
                println(targetHash.toBase58())
                require(loadedHash == targetHash)
            }
            logger.info("Done!")
        }
    }
}
