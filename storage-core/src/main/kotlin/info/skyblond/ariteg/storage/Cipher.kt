package info.skyblond.ariteg.storage

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom

internal object Cipher {
    private val secureRandom = SecureRandom()

    /**
     * Key: 256bits -> 32bytes
     * Using AES-256-GCM-SIV, max size: 2^31 - 24 bytes
     * */
    internal fun encrypt(key: ByteArray, plainData: ByteArray): ByteArray {
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
    internal fun decrypt(key: ByteArray, encrypted: ByteArray): ByteArray {
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
}
