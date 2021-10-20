package info.skyblond.ariteg.proto.meta

import info.skyblond.ariteg.proto.AbstractProtoServiceTest
import info.skyblond.ariteg.proto.ProtoWriteService
import info.skyblond.ariteg.proto.getProxyApacheClientBuilder
import info.skyblond.ariteg.proto.storage.InMemoryProtoStorageService
import kotlin.random.Random
import org.junit.jupiter.api.BeforeEach
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient


class NaiveDynamoDBProtoServiceTest : AbstractProtoServiceTest() {
    private val dynamoDBClient = DynamoDbClient.builder()
        .region(Region.US_WEST_2)
        .httpClientBuilder(getProxyApacheClientBuilder())
        .build()

    private val tableName = "skyblond-ariteg-develop-test-202110"

    override val storageService = InMemoryProtoStorageService(primaryProvider, secondaryProvider)
    override val metaService = NaiveDynamoDBProtoMetaService(dynamoDBClient, tableName)

    // wait random time if locking failed
    override val protoService = object : ProtoWriteService(
        metaService, storageService,
        { 10_000 + Random.nextLong(1_000, 10_000) }
    ) {}

    @BeforeEach
    fun deleteAllItem() {
        dynamoDBClient.scan {
            it.tableName(tableName)
                .attributesToGet(NaiveDynamoDBProtoMetaService.Companion.primaryHashKeyName)
        }.items().forEach { item ->
            dynamoDBClient.deleteItem {
                it.tableName(tableName)
                    .key(item)
            }
        }
        Thread.sleep(5000)
    }

    override fun cleanUpAfterEachTest() {
        // clean files
        dynamoDBClient.close()
    }

}
