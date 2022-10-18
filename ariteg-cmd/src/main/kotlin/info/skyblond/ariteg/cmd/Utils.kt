package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link
import info.skyblond.ariteg.ListObject
import info.skyblond.ariteg.TreeObject
import info.skyblond.ariteg.cmd.CmdContext.storage
import java.math.BigInteger

fun Entry.printDetails() {
    println("-------------------- Entry --------------------")
    println(this.id)
    println("\tName: ${this.name}")
    println("\tTime: ${this.time}")
    println("\tSize: ${calculateFileSize(this.link)} bytes")
    println("\tNote: ${this.note}")
}

fun calculateFileSize(link: Link): BigInteger {
    return when (link.type) {
        Link.Type.BLOB -> link.size.toBigInteger()

        Link.Type.LIST -> (storage.read(link).get() as ListObject).content
            // calculate sub elem
            .map { calculateFileSize(link) }
            .sumOf { it }

        Link.Type.TREE -> (storage.read(link).get() as TreeObject).content
            .map { calculateFileSize(link) }
            .sumOf { it }
    }
}

fun String.asOtcInt(): Int = this.dropWhile { it == '0' }.let { it.ifEmpty { "0" } }.toInt(radix = 8)

