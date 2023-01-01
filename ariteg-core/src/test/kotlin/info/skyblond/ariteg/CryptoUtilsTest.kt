package info.skyblond.ariteg

import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom

internal class CryptoUtilsTest {

    @Test
    fun testCalculateHash1() {
        val samples = listOf(
            "Ariteg" to "3cd2e3c24733da354ccc574f016568bbcf2594a012c697a75803391cad9ad42f52506fd8eada5e7b3d5b0814d12a8a6b1b7d1c5e716c05228835587e0569d2a1",
            "hurui200320" to "f84d3838ffdd8c35c383dcca07daf0e7f1fe6abc01ce235567070f7bb4a117aed75f29f8c2c6945e060f2df2dba7240e64993f0c9e54c7e210c38b9f413e8b15",
            "" to "a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26",
        )

        samples.forEach { (value, expectHex) ->
            val hash = calculateHash1(value.encodeToByteArray()).hash
            val expect = expectHex.decodeHex()
            Assertions.assertArrayEquals(expect, hash)
        }
    }

    @Test
    fun testCalculateHash2() {
        val samples = listOf(
            "Ariteg" to "f6e8d7cba92001473892e95032336b6142c510ca9f3f9b90a6e56f4e258a40b8dc12b70bf5d31930ad82d81f64bfa418c9185eb570901f6f713807e1c36d1f20",
            "hurui200320" to "ba60d1624c9861bfd1842209eb6cc4ef6defac597f6cd1805db5b1db67aeab303e0e1afa2f661b02d6f44ddad348c507ca3186741e9cd6851806f4774f9b43b4",
            " " to "ae3aa4b26b87ab60e8ef52430e992ad8c360d0a486153401b46a21096c96e2341dcc2ee584e36b2222016d1f120edc8944d5ab1022678466883fe9e8c0761a53",
        )

        samples.forEach { (value, expectHex) ->
            val hash = calculateHash2(value.encodeToByteArray()).hash
            val expect = expectHex.decodeHex()
            Assertions.assertArrayEquals(expect, hash)
        }
    }

    private val secureRandom = SecureRandom()

    @Test
    fun testEncryptCorrect() {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        val plain = "天匠染青红，花腰呈袅娜。".encodeToByteArray()
        val encrypted = encrypt(key, plain)
        val decrypted = decrypt(key, encrypted)
        Assertions.assertArrayEquals(plain, decrypted)
    }

    @Test
    fun testDecryptWrong() {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        val plain = "天匠染青红，花腰呈袅娜。".encodeToByteArray()
        val encrypted = encrypt(key, plain)
        secureRandom.nextBytes(key)
        assertThrows<InvalidCipherTextException> {
            decrypt(key, encrypted)
        }
    }
}
