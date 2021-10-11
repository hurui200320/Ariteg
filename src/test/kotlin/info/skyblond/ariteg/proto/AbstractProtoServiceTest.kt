package info.skyblond.ariteg.proto

import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.multihash.MultihashProviders
import info.skyblond.ariteg.proto.meta.ProtoMetaService
import info.skyblond.ariteg.proto.storage.InMemoryProtoStorageService
import info.skyblond.ariteg.proto.storage.ProtoStorageService
import io.ipfs.multihash.Multihash
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random

abstract class AbstractProtoServiceTest(
    protected val defaultBlobSize: Int = 4 * 1024, // 4KB
    protected val defaultListSize: Int = 32,
    protected val primaryProvider: MultihashProvider = MultihashProviders.sha3Provider512(),
    protected val secondaryProvider: MultihashProvider = MultihashProviders.blake2b512Provider(),
) {
    protected abstract val storageService: ProtoStorageService
    protected abstract val metaService: ProtoMetaService
    protected abstract val protoService: ProtoWriteService

    private val testFileSize = listOf(
        0L, // empty file
        defaultBlobSize - 3L, // small file
        3L * defaultBlobSize, // middle file
        (defaultListSize + 3L) * defaultBlobSize, // big file
        3L * defaultListSize * defaultBlobSize, // large file
    )

    protected abstract fun cleanUpAfterEachTest()

    @AfterEach
    private fun tearDown() {
        // close storage first, wait all task finished
        storageService.close()
        // then close the metadata
        metaService.close()
        // do subclass clean up
        cleanUpAfterEachTest()
    }

    protected fun prepareTestFile(size: Long): File {
        val file = File.createTempFile(System.currentTimeMillis().toString(), System.nanoTime().toString())
        val buffer = ByteArray(4096) // 4KB
        file.outputStream().use { outputStream ->
            var counter = 0L
            while (counter < size) {
                Random.nextBytes(buffer)
                outputStream.write(buffer, 0, min(buffer.size.toLong(), size - counter).toInt())
                counter += buffer.size
            }
        }
        return file
    }

    protected fun writeAndWait(inputStream: InputStream): AritegLink {
        val (link, futureList) = inputStream.use {
            protoService.writeChunk(
                "", it,
                defaultBlobSize, defaultListSize
            )
        }
        // wait all writing is finished
        Assertions.assertDoesNotThrow {
            futureList.forEach { it.get() }
        }
        return link
    }

    protected fun calculatePrimaryHash(inputStream: InputStream): Multihash {
        return inputStream.use { primaryProvider.digest(it, 4096 * 1024) }
    }

    protected fun simpleRead(target: AritegLink): InputStream {
        val proto = storageService.loadProto(target)
        // Is blob, return the data as input stream
        if (proto.type == ObjectType.BLOB)
            return ByteArrayInputStream(proto.data.toByteArray())

        // Is list
        require(proto.type == ObjectType.LIST) { "Unsupported link type: ${proto.type}" }
        return object : InputStream() {
            val linkList = mutableListOf<AritegLink>()
            var currentBlob: AritegObject? = null
            var pointer = 0

            init {
                // the initial link list is from proto
                linkList.addAll(proto.linksList)
            }

            private fun fetchNextBlob() {
                // make sure we have more links to read
                while (linkList.isNotEmpty() && currentBlob == null) {
                    // fetch the first link
                    val link = linkList.removeAt(0)
                    val obj = storageService.loadProto(link)
                    if (obj.type == ObjectType.BLOB) {
                        // is blob, use it
                        currentBlob = obj
                        pointer = 0
                        break // break the loop, we are done
                    } else {
                        // else, is list, add them to the list and try again
                        require(obj.type == ObjectType.LIST) { "Unsupported object type: ${proto.type}" }
                        linkList.addAll(0, obj.linksList)
                    }
                }
            }

            override fun read(): Int {
                if (currentBlob == null) {
                    do {
                        // refresh blob
                        fetchNextBlob()
                    } while (currentBlob != null && currentBlob!!.data.size() == 0)
                    // if we get empty blob, skip it and fetch next
                    // stop when getting null blob
                }
                if (currentBlob == null) {
                    // really the end
                    return -1
                }
                // we got the blob, read the value
                val result = currentBlob!!.data.byteAt(pointer++).toUByte().toInt()
                assert(result != -1) { "Unexpected EOF" }
                if (pointer >= currentBlob!!.data.size()) {
                    // if we are the end of blob, release it
                    currentBlob = null
                }
                return result
            }
        }
    }

    /**
     * This test the basic function of given services.
     * It performs read and write to different size of data chunk, and
     * check if everything is good.
     * */
    @Test
    fun testChunkReadAndWrite() {
        testFileSize.forEach { size ->
            // prepare test file
            val file = prepareTestFile(size)
            val targetHash = calculatePrimaryHash(file.inputStream())
            val link = writeAndWait(file.inputStream())
            val loadedHash = calculatePrimaryHash(simpleRead(link))
            Assertions.assertEquals(targetHash, loadedHash)
        }
    }

    /**
     * This test will create racing conditions on metadata service.
     * Write counts is only supported by [InMemoryProtoStorageService].
     * Will skip if the [storageService] is not [InMemoryProtoStorageService].
     * */
    @Test
    fun testMetaMultiThread() {
        Assumptions.assumeTrue(
            storageService is InMemoryProtoStorageService,
            "Storage backend is not in memory"
        )
        val threadCount = Runtime.getRuntime().availableProcessors() * 5
        val pieceCount = defaultListSize + 1
        val file = prepareTestFile(defaultBlobSize.toLong() * pieceCount)
        val executor = Executors.newCachedThreadPool()
        val barrier = CyclicBarrier(threadCount + 1)
        val futureList = (1..threadCount).map {
            executor.submit(Callable {
                file.inputStream().use {
                    barrier.await()
                    // start and the same time
                    protoService.writeChunk("", it, defaultBlobSize, defaultListSize)
                }
            })
        }
        barrier.await()
        val result = futureList.map { task ->
            task.get()
        }
        // make sure every thread gives the same result
        Assertions.assertEquals(1, result.map { it.first }.distinct().size)
        // check every writing request success
        Assertions.assertDoesNotThrow {
            result.flatMap { it.second }.forEach { it.get() }
        }
        // extra 1 for additional list objects
        // link -> listObj -> (listObj, blobObj)
        //                       |
        //                       +-> (blobObj x listSize)
        // Be careful with the type
        Assertions.assertEquals(
            pieceCount + 2L,
            (storageService as InMemoryProtoStorageService).getWriteCount()
        )
        executor.shutdown()
    }
}
