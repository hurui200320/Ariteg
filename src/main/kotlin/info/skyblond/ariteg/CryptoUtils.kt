package info.skyblond.ariteg

import io.ipfs.multihash.Multihash
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Calculate hash 1: SHA3-512
 * */
fun calculateHash1(data: ByteArray): Multihash {
    val digest = MessageDigest.getInstance("SHA3-512")
    val output = digest.digest(data)
    return Multihash(Multihash.Type.sha3_512, output)
}

/**
 * Calculate hash 2: blake2b-512
 * */
fun calculateHash2(data: ByteArray): Multihash {
    val digest = Blake2bDigest(512)
    val output = ByteArray(digest.digestSize)
    digest.update(data, 0, data.size)
    digest.doFinal(output, 0)
    return Multihash(Multihash.Type.blake2b_512, output)
}

private val secureRandom = SecureRandom()

/**
 * Key: 256bits -> 32bytes
 * Using AES-256-GCM-SIV, max size: 2^31 - 24 bytes
 * */
fun encrypt(key:ByteArray, plainData: ByteArray): ByteArray {
    val cipher = GCMSIVBlockCipher(AESEngine())
    // nonce can be any size, but must not be reused
    // the SIV will have more security when nonce is reused
    val nonce = ByteArray(12)
    secureRandom.nextBytes(nonce)
    // but the max message it can handle is 2^31 - 24 bytes
    require(plainData.size <= 1024 * 1024 * 1024) { "AES-256-GCM-SIV cannot handle this large amount of bytes" }
    // mac size is ranged from 32~128 bits, for message integrity check
    cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
    val result = ByteArray(cipher.getOutputSize(plainData.size))
    val resultSize = cipher.processBytes(plainData, 0, plainData.size, result, 0)
    cipher.doFinal(result, resultSize)
    return nonce + result
}

/**
 * Key: 256bits -> 32bytes
 * Using AES-256-GCM-SIV, max size: 2^31 - 24 bytes
 * */
fun decrypt(key: ByteArray, encrypted: ByteArray): ByteArray {
    val nonce = ByteArray(12)
    val encryptedMessage = ByteArray(encrypted.size - 12)
    System.arraycopy(encrypted, 0, nonce, 0, nonce.size)
    System.arraycopy(encrypted, 12, encryptedMessage, 0, encryptedMessage.size)

    val cipher = GCMSIVBlockCipher(AESEngine())
    cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
    val result = ByteArray(cipher.getOutputSize(encryptedMessage.size))
    val resultSize = cipher.processBytes(encryptedMessage, 0, encryptedMessage.size, result, 0)
    cipher.doFinal(result, resultSize)
    return result
}
