package info.skyblond.ariteg.proto.storage

import info.skyblond.ariteg.proto.AbstractProtoServiceTest
import info.skyblond.ariteg.proto.ProtoWriteService
import info.skyblond.ariteg.proto.meta.InMemoryProtoMetaService
import java.io.File
import kotlin.random.Random


class FileProtoServiceTest : AbstractProtoServiceTest() {
    private val dataBaseDir = File("./data/test_data_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
        .also { it.mkdirs() }

    override val storageService = FileProtoStorageService(
        dataBaseDir, Runtime.getRuntime().availableProcessors(), primaryProvider, secondaryProvider
    )
    override val metaService = InMemoryProtoMetaService()
    override val protoService = object : ProtoWriteService(metaService, storageService, 5000) {}

    override fun cleanUpAfterEachTest() {
        // clean files
        dataBaseDir.deleteRecursively()
    }

}
