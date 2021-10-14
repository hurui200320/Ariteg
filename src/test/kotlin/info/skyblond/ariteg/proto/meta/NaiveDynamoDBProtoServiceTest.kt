package info.skyblond.ariteg.proto.meta

import info.skyblond.ariteg.proto.AbstractProtoServiceTest
import info.skyblond.ariteg.proto.ProtoWriteService
import info.skyblond.ariteg.proto.getProxyApacheClientBuilder
import info.skyblond.ariteg.proto.meta.mapdb.MapDBProtoMetaService
import info.skyblond.ariteg.proto.storage.FileProtoStorageService
import info.skyblond.ariteg.proto.storage.InMemoryProtoStorageService
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client
import java.io.File
import kotlin.random.Random


class NaiveDynamoDBProtoServiceTest : AbstractProtoServiceTest() {
    private val dynamoDBClient = DynamoDbClient.builder()
        .region(Region.US_WEST_2)
        .httpClientBuilder(getProxyApacheClientBuilder())
        .build()

    override val storageService = InMemoryProtoStorageService(primaryProvider, secondaryProvider)
    override val metaService = NaiveDynamoDBProtoMetaService(dynamoDBClient, "skyblond-ariteg-develop-test-202110")
    // wait 20s if locking failed
    override val protoService = object : ProtoWriteService(metaService, storageService, 20_000) {}

    override fun cleanUpAfterEachTest() {
        // clean files
        dynamoDBClient.close()
    }

}
