package info.skyblond.ariteg.multihash

import io.ipfs.multihash.Multihash
import org.bouncycastle.crypto.digests.Blake2bDigest
import java.security.MessageDigest

object MultihashProviders {
    @JvmStatic
    fun sha3Provider512(): MultihashJavaProvider {
        return MultihashJavaProvider(Multihash.Type.sha3_512) {
            MessageDigest.getInstance("SHA3-512")
        }
    }

    @JvmStatic
    fun sha3Provider256(): MultihashJavaProvider {
        return MultihashJavaProvider(Multihash.Type.sha3_256) {
            MessageDigest.getInstance("SHA3-256")
        }
    }

    // TODO Blake3 in multihash still in draft
//    fun blake3Provider(): MultihashBouncyCastleProvider {
//        return MultihashBouncyCastleProvider(Multihash.Type.blake3) {
//            Blake3Digest()
//        }
//    }

    @JvmStatic
    fun blake2b512Provider(): MultihashBouncyCastleProvider {
        return MultihashBouncyCastleProvider(Multihash.Type.blake2b_512) {
            Blake2bDigest(512)
        }
    }

    @JvmStatic
    fun blake2b256Provider(): MultihashBouncyCastleProvider {
        return MultihashBouncyCastleProvider(Multihash.Type.blake2b_256) {
            Blake2bDigest(256)
        }
    }
}
