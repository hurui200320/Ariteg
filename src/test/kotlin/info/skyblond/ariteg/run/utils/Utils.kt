package info.skyblond.ariteg.run.utils

import info.skyblond.ariteg.Entry

fun Entry.printDetails() {
    println("-------------------- Entry --------------------")
    println(this.id)
    println("\tName: ${this.name}")
    println("\tNote: ${this.note}")
    println("\tTime: ${this.time}")
}
