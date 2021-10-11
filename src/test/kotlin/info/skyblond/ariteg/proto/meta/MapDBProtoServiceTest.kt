package info.skyblond.ariteg.proto.meta

import info.skyblond.ariteg.proto.AbstractProtoServiceTest
import info.skyblond.ariteg.proto.ProtoWriteService
import info.skyblond.ariteg.proto.meta.mapdb.MapDBProtoMetaService
import info.skyblond.ariteg.proto.storage.FileProtoStorageService
import info.skyblond.ariteg.proto.storage.InMemoryProtoStorageService
import java.io.File
import kotlin.random.Random


class MapDBProtoServiceTest : AbstractProtoServiceTest() {
    private val dataBaseDir = File("./data/test_data_dir_${System.currentTimeMillis()}_${Random.nextInt()}")
        .also { it.mkdirs() }

    override val storageService = InMemoryProtoStorageService(primaryProvider, secondaryProvider)
    override val metaService = MapDBProtoMetaService(File(dataBaseDir, "client.db"))
    override val protoService = object : ProtoWriteService(metaService, storageService, 5000) {}

    override fun cleanUpAfterEachTest() {
        // clean files
        dataBaseDir.deleteRecursively()
    }

}
