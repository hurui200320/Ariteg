package info.skyblond.ariteg.file

import info.skyblond.ariteg.multihash.MultihashProviders
import info.skyblond.ariteg.proto.ProtoWriteService
import info.skyblond.ariteg.proto.meta.InMemoryProtoMetaService
import info.skyblond.ariteg.proto.storage.FileProtoStorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class PrototypeWritingTest {

    private val baseDir = File("./data/test")

    @BeforeEach
    fun setUp() {
        baseDir.mkdirs()
    }

    @AfterEach
    fun cleanUp() {
//        baseDir.deleteRecursively()
    }

    @Test
    fun run() {
        val protoMetaService = InMemoryProtoMetaService()
        val protoStorageService = FileProtoStorageService(
            baseDir, Runtime.getRuntime().availableProcessors(),
            MultihashProviders.sha3Provider512(), MultihashProviders.blake2b512Provider()
        )
        val protoWriteService = object : ProtoWriteService(
            protoMetaService, protoStorageService, { 5_000 }
        ) {}
        val fileIndexService = InMemoryFileIndexService(protoMetaService)
        val fileWriteService = object : FileWriteService(
            fileIndexService, protoWriteService,
            1 * 1024 * 1024 /* 1MB */, 176
        ) {}

        val entry = fileWriteService.write(
            "test/sub", "【VCB-s】冰果",
            File("E:\\20210910_d\\Tixati\\【VCB-s】日常")
        )
        println(entry)
        println("-".repeat(10))
        fileIndexService.dumpContent().values.forEach { println(it) }
    }
}
