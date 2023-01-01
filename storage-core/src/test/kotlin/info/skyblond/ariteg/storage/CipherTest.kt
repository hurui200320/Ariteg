package info.skyblond.ariteg.storage

import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom

internal class CipherTest {

    private val secureRandom = SecureRandom()

    @Test
    fun testEncryptCorrect() {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        val plain = "天匠染青红，花腰呈袅娜。".encodeToByteArray()
        val encrypted = Cipher.encrypt(key, plain)
        val decrypted = Cipher.decrypt(key, encrypted)
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun testDecryptWrong() {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        val plain = "天匠染青红，花腰呈袅娜。".encodeToByteArray()
        val encrypted = Cipher.encrypt(key, plain)
        secureRandom.nextBytes(key)
        assertThrows<InvalidCipherTextException> {
            Cipher.decrypt(key, encrypted)
        }
    }
}
