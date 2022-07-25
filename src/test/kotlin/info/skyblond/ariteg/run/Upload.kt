package info.skyblond.ariteg.run

import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.run.utils.getSlicerProvider
import info.skyblond.ariteg.run.utils.printDetails
import info.skyblond.ariteg.run.utils.useStorageSafe
import java.io.File

fun main() {
    // 206G
    val file = File("/mnt/coco-adel/temp/2016第一块移动硬盘//2016移动硬盘-软件")

    useStorageSafe { logger, storage ->
        val slicerProvider = getSlicerProvider()

        val entry = Operations.digest(file, slicerProvider, storage)
        logger.info { "Finished ${entry.name}" }
        entry.printDetails()

        logger.info { "Done" }
    }
}
