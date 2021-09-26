package info.skyblond.ariteg.multihash

import io.ipfs.multihash.Multihash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MultihashProvidersTest {
    // Use empty string as test, since the output can be confirmed on the internet
    private val emptyInput = ByteArray(0)

    @Test
    fun sha3Provider512Test() {
        val digest = MultihashProviders.sha3Provider512()
        val result = digest.digest(emptyInput)
        assertEquals(
            Multihash.fromBase58("8tXPg8VsVLVrv2sSepoKfnbqoFa94UQ3bQw21qgyajHFgbVBRtKLvTjHA33fiRY29RGK1KoEff776Ly8UrUh4dBTnV"),
            result
        )
    }

    @Test
    fun sha3Provider256Test() {
        val digest = MultihashProviders.sha3Provider256()
        val result = digest.digest(emptyInput)
        assertEquals(
            Multihash.fromBase58("W1kknXZLRvyN91meETWtiTKmiAYM4HNtyHekcEPZXYB8Tj"),
            result
        )
    }

    @Test
    fun blake2b512ProviderTest() {
        val digest = MultihashProviders.blake2b512Provider()
        val result = digest.digest(emptyInput)
        assertEquals(
            Multihash.fromBase58("SEfXUCRqQ9o9q17v3B1UoSXmRVZMsUVCrjkc4SR6h2Btpzbqe27J55bxSvpb3hCHDKCekJXB12sJAQcD1RT1nXcEW3ejP"),
            result
        )
    }

    @Test
    fun blake2b256ProviderTest() {
        val digest = MultihashProviders.blake2b256Provider()
        val result = digest.digest(emptyInput)
        assertEquals(
            Multihash.fromBase58("2Drjgb5DseoVAvRLngcVmd4YfJAi3J1145kiNFV3CL32Hs6vzb"),
            result
        )
    }
}
