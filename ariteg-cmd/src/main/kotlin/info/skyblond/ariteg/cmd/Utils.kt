package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.cmd.fuse.RandomAccessCache.calculateFileSize
import org.apache.commons.io.FileUtils.byteCountToDisplaySize

fun Entry.printDetails() {
    println("-------------------- Entry --------------------")
    println(this.id)
    println("\tName: ${this.name}")
    println("\tTime: ${this.time}")
    println("\tSize: ${byteCountToDisplaySize(calculateFileSize(this.link))}")
    println("\tNote: ${this.note}")
}


fun String.asOtcInt(): Int = this.dropWhile { it == '0' }.let { it.ifEmpty { "0" } }.toInt(radix = 8)

