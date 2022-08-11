package info.skyblond.ariteg.slicers.rolling

import java.io.File

class RabinKarpSlicer(
    file: File,
    targetFingerprint: UInt,
    fingerprintMask: UInt,
    minChunkSize: Int,
    maxChunkSize: Int,
    windowSize: Int,
    /**
     * The prime number `P` in algorithm
     * */
    private val prime: UInt,
    channelBufferSize: Int = 32 * 1024 * 1024
) : RollingHashSlicer(
    file, targetFingerprint, fingerprintMask,
    minChunkSize, maxChunkSize, windowSize, channelBufferSize
) {

    private var pPowN: UInt = 0u

    override fun initWindowHash(bytes: Array<UInt>): UInt {
        this.pPowN = 1u
        var hash: UInt = 0u
        // hash = P^(N-1)*b[0] + P^(N-2)*b[1] + ... + P * b[N-2] + b[N-1]
        // ---> = ((((...((b[0] * P + b[1]) * P) * P + ...) * P + b[N-1]
        // The b[0] will get P^(N-1), b[1] will get P^[N-2]
        // The b[N-2] will get P, and b[N-1] will get P^0
        for (b in bytes) {
            // hash' = hash * P + b
            hash = hash * this.prime + b
            // calculate P^N
            this.pPowN *= this.prime
        }
        return hash
    }

    /**
     * Calculate next hash.
     * <p>
     * hash(next) = { hash(prev) * P - P^N * outgoing + incoming } % m
     * <p>
     * The `% m` is performed by [UInt] overflow.
     */
    override fun calcNextHash(currentHash: UInt, inByte: UInt, outByte: UInt): UInt =
        currentHash * prime - pPowN * outByte + inByte

}
