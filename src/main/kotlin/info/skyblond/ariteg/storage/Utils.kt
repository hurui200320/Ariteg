package info.skyblond.ariteg.storage

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import io.ipfs.multihash.Multihash
import java.io.InputStream
import java.security.MessageDigest

fun doSHA3Multihash512(bytes: ByteArray): Multihash {
    val digest = MessageDigest.getInstance("SHA3-512")
    val encodedHash = digest.digest(bytes)
    return Multihash(Multihash.Type.sha3_512, encodedHash)
}

@Deprecated("This method is for test only, should not use in code")
fun calculateSha3(inputStream: InputStream, bufferSize: Int): Multihash {
    val digest = MessageDigest.getInstance("SHA3-512")
    val buffer = ByteArray(bufferSize)
    var bytesReadCount: Int
    while (inputStream.read(buffer, 0, buffer.size).also { bytesReadCount = it } != -1) {
        digest.update(buffer, 0, bytesReadCount)
    }
    return Multihash(Multihash.Type.sha3_512, digest.digest())
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
