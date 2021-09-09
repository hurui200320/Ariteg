package info.skyblond.ariteg

import com.google.protobuf.util.JsonFormat
import io.ipfs.multihash.Multihash
import java.security.MessageDigest


object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val digest = MessageDigest.getInstance("SHA3-512")
        val encodedHash = digest.digest(
            "originalString".toByteArray(Charsets.UTF_8)
        )
        println(encodedHash.toHex())

        val multiHash = Multihash(Multihash.Type.sha3_512, encodedHash)
        println(multiHash.toBase58())

        val obj = AritegLink.newBuilder()
            .build()
        println(
            JsonFormat.printer().print(
                obj
            )
        )
        println(obj.toByteArray().toHex())
        println(obj.toByteArray().size)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}
