package info.skyblond.ariteg.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import io.ipfs.multihash.Multihash


fun ByteString.toMultihash(): Multihash {
    return Multihash.deserialize(this.toByteArray())
}

fun AritegLink.toMultihashBase58(): String {
    return this.multihash.toMultihash().toBase58()
}

//fun AritegObject.toMultihashBase58(): String {
//    val rawBytes = this.toByteArray()
//    val multihash = doSHA3Multihash512(rawBytes)
//    return multihash.toBase58()
//}
