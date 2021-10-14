package info.skyblond.ariteg.proto.storage

import info.skyblond.ariteg.proto.AbstractProtoServiceTest
import info.skyblond.ariteg.proto.ProtoWriteService
import info.skyblond.ariteg.proto.getProxyApacheClientBuilder
import info.skyblond.ariteg.proto.meta.InMemoryProtoMetaService
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

internal class NaiveS3StorageServiceTest : AbstractProtoServiceTest() {
    private val s3Client = S3Client.builder()
        .region(Region.US_WEST_2)
        .httpClientBuilder(getProxyApacheClientBuilder())
        .build()

    override val storageService = NaiveS3StorageService(
        s3Client, "skyblond-ariteg-develop-test-202110",
        Runtime.getRuntime().availableProcessors(),
        primaryProvider, secondaryProvider
    )
    override val metaService = InMemoryProtoMetaService()
    override val protoService = object : ProtoWriteService(metaService, storageService, 5000) {}

    override fun cleanUpAfterEachTest() {
        // stop s3
        s3Client.close()
    }
}
