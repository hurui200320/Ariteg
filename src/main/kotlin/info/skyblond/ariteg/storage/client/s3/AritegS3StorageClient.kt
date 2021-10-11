package info.skyblond.ariteg.storage.client.s3

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeyType

class AritegS3StorageClient(
    private val dynamoDbClient: DynamoDbClient,
    private val dynamoTableName: String,
) {
    companion object {
        private const val tablePartitionKeyName = "primaryHash"
    }

    init {
        // check table, ResourceNotFoundException will be thrown if table not exist.
        val table = dynamoDbClient.describeTable(
            DescribeTableRequest.builder().tableName(dynamoTableName).build()
        ).table()
        assert(table.tableName() == dynamoTableName)
        assert(table.keySchema().size == 1) { "No sort key allowed" }
        val keyName = checkNotNull(
            table.keySchema()
                .find { it.keyType() == KeyType.HASH }
                ?.attributeName()
        ) { "No partition key is defined in the giving table" }
        assert(keyName == tablePartitionKeyName) { "Partition key must be $tablePartitionKeyName" }
    }

}
