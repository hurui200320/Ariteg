package info.skyblond.ariteg.storage.client.s3

import info.skyblond.ariteg.ObjectType
import info.skyblond.ariteg.multihash.MultihashProvider
import info.skyblond.ariteg.multihash.MultihashProviders
import io.ipfs.multihash.Multihash
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import java.util.*

class AritegS3StorageClient(
    private val s3Client: S3Client,
    private val s3BucketName: String,
    private val dynamoDbClient: DynamoDbClient,
    private val dynamoTableName: String,
    private val primaryMultihashProvider: MultihashProvider = MultihashProviders.sha3Provider512(),
    private val secondaryMultihashProvider: MultihashProvider = MultihashProviders.blake2b512Provider(),
) {
    private val clientId = UUID.randomUUID()

    companion object {
        private const val tablePartitionKeyName = "primaryHash"
    }

    init {
        // check table, ResourceNotFoundException will be thrown if table not exist.
        val table = dynamoDbClient.describeTable(
            DescribeTableRequest.builder().tableName(dynamoTableName).build()
        ).table()
        assert(table.tableName() == dynamoTableName)
        val keyName = checkNotNull(
            table.keySchema()
                .find { it.keyType() == KeyType.HASH }
                ?.attributeName()
        ) { "No partition key is defined in the giving table" }
        assert(keyName == tablePartitionKeyName) { "Partition key must be $tablePartitionKeyName" }
    }

    data class DynamoEntity(
        val primaryMultihash: Multihash,
        val secondaryMultihash: Multihash,
        val type: ObjectType
    ) {
        companion object {
            fun fromItem(item: Map<String, AttributeValue>): DynamoEntity {
                TODO()
            }
        }

        fun toItem(): Map<String, AttributeValue> {
            val item: MutableMap<String, AttributeValue> = HashMap()
            item[tablePartitionKeyName] =
                AttributeValue.builder().b(SdkBytes.fromByteArray(primaryMultihash.toBytes())).build()
            item["secondaryHash"] =
                AttributeValue.builder().b(SdkBytes.fromByteArray(secondaryMultihash.toBytes())).build()
            item["type"] = AttributeValue.builder().s(type.toString()).build()
            return item
        }

    }

}
