package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.slicers.Slicer
import info.skyblond.ariteg.slicers.rolling.RabinKarpSlicer

private data class FixedSlicerConfig(
    /**
     * Default chunk size: 1MB
     * */
    val chunkSizeInByte: Int = 1024 * 1024
)

private data class RollingSlicerConfig(
    /**
     * fingerprint mask is 11..11b (20 times of 1).
     * meaning the average blob size is 1MB
     * */
    val fingerprintMask: UInt = (1u shl 20) - 1u,
    /**
     * min chunk size is 512KB
     * */
    val minChunkSize: Int = 512 * 1024,
    /**
     * max chunk size is 8MB
     * */
    val maxChunkSize: Int = 8 * 1024 * 1024,
    /**
     * default window size is 64B
     * */
    val windowSize: Int = 64,
    /**
     * target fingerprint is normally 0
     * */
    val targetFingerprint: UInt = 0u,
    /**
     * default channel buffer size (read buffer) is 32MB
     * */
    val channelBufferSize: Int = 32 * 1024 * 1024
)

private data class RabinKarpSlicerConfig(
    val prime: UInt = 1821497u
)

fun getSlicer(): Slicer = (RollingSlicerConfig() to RabinKarpSlicerConfig())
    .let { (rollingConfig, rabinKarpConfig) ->
        RabinKarpSlicer(
            targetFingerprint = rollingConfig.targetFingerprint,
            fingerprintMask = rollingConfig.fingerprintMask,
            minChunkSize = rollingConfig.minChunkSize,
            maxChunkSize = rollingConfig.maxChunkSize,
            windowSize = rollingConfig.windowSize,
            prime = rabinKarpConfig.prime,
            channelBufferSize = rollingConfig.channelBufferSize
        )
    }
