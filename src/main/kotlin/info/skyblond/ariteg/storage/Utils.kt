package info.skyblond.ariteg.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import io.ipfs.multihash.Multihash
import java.security.MessageDigest

fun doSHA3Multihash512(bytes: ByteArray): Multihash {
    val digest = MessageDigest.getInstance("SHA3-512")
    val encodedHash = digest.digest(bytes)
    return Multihash(Multihash.Type.sha3_512, encodedHash)
}

fun ByteString.toMultihash(): Multihash {
    return Multihash.deserialize(this.toByteArray())
}

fun AritegLink.toMultihashBase58(): String {
    return this.multihash.toMultihash().toBase58()
}

fun AritegObject.toMultihashBase58(): String {
    val rawBytes = this.toByteArray()
    val multihash = doSHA3Multihash512(rawBytes)
    return multihash.toBase58()
}
