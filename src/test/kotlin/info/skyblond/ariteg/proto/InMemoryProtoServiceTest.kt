package info.skyblond.ariteg.proto

import info.skyblond.ariteg.proto.meta.InMemoryProtoMetaService
import info.skyblond.ariteg.proto.storage.InMemoryProtoStorageService

class InMemoryProtoServiceTest : AbstractProtoServiceTest() {
    override val storageService = InMemoryProtoStorageService(primaryProvider, secondaryProvider)
    override val metaService = InMemoryProtoMetaService()
    override val protoService = object : ProtoWriteService(metaService, storageService, { 5000 }) {}

    override fun cleanUpAfterEachTest() {

    }
}
