package info.skyblond.ariteg.proto.meta

import info.skyblond.ariteg.ObjectType
import io.ipfs.multihash.Multihash
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

/**
 * Use longer wait time if the racing condition is intense
 * */
class NaiveDynamoDBProtoMetaService(
    private val dynamoDbClient: DynamoDbClient,
    private val dynamoTableName: String,
) : ProtoMetaService {
    companion object {
        private const val primaryHashKeyName = "primaryHash"
        private const val secondaryHashKeyName = "secondaryHash"

        // Note: `type` is a reserved keyword in DynamoDB
        private const val typeKeyName = "objectType"

        // Note: `temp` is also a reserved keyword in DynamoDB
        private const val tempKeyName = "tempValue"
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
        assert(keyName == primaryHashKeyName) { "Partition key must be $primaryHashKeyName" }
    }

    private fun ProtoMetaService.Entry.toItem(): Map<String, AttributeValue> =
        HashMap<String, AttributeValue>().also {
            it[primaryHashKeyName] =
                AttributeValue.builder().b(SdkBytes.fromByteArray(primaryMultihash.toBytes())).build()
            it[secondaryHashKeyName] =
                AttributeValue.builder().b(SdkBytes.fromByteArray(secondaryMultihash.toBytes())).build()
            it[typeKeyName] = AttributeValue.builder().s(type.name).build()
            it[tempKeyName] = AttributeValue.builder().apply {
                if (temp == null)
                    nul(true)
                else
                    n(temp.toString())
            }.build()
        }

    private fun Map<String, AttributeValue>.toEntry(): ProtoMetaService.Entry =
        ProtoMetaService.Entry(
            primaryMultihash = Multihash.deserialize(this[primaryHashKeyName]!!.b().asByteArray()),
            secondaryMultihash = Multihash.deserialize(this[secondaryHashKeyName]!!.b().asByteArray()),
            type = ObjectType.valueOf(this[typeKeyName]!!.s()),
            temp = this[tempKeyName]!!.n()?.toLong()
        )

    private fun Multihash.toKey(): Map<String, AttributeValue> =
        HashMap<String, AttributeValue>().also {
            it[primaryHashKeyName] = AttributeValue.builder().b(
                SdkBytes.fromByteArray(this.toBytes())
            ).build()
        }


    override fun getByPrimaryMultihash(primaryMultihash: Multihash): ProtoMetaService.Entry? {
        val resp = dynamoDbClient.getItem(
            GetItemRequest.builder()
                .tableName(dynamoTableName)
                .key(primaryMultihash.toKey())
                .consistentRead(true)
                .build()
        )
        return if (resp.hasItem())
            resp.item().toEntry()
        else
            null
    }

    override fun compareAndSetTempFlag(primaryMultihash: Multihash, oldValue: Long, newValue: Long?): Long? {
        return try {
            dynamoDbClient.updateItem(
                UpdateItemRequest.builder()
                    .tableName(dynamoTableName)
                    .key(primaryMultihash.toKey())
                    .conditionExpression("$tempKeyName = :oldValue")
                    .updateExpression("SET $tempKeyName = :newValue")
                    .expressionAttributeValues(mutableMapOf<String, AttributeValue>().also {
                        it[":oldValue"] = AttributeValue.builder().n(oldValue.toString()).build()
                        it[":newValue"] = AttributeValue.builder().apply {
                            if (newValue == null)
                                nul(true)
                            else
                                n(newValue.toString())
                        }.build()
                    })
                    .build()
            )
            newValue
        } catch (e: ConditionalCheckFailedException) {
            // update failed, get the latest value
            // TODO not atomic, is it ok?
            val entry = getByPrimaryMultihash(primaryMultihash)
                ?: throw IllegalStateException("Entry exists but not found: ${primaryMultihash.toBase58()}")
            entry.temp
        }
    }

    override fun saveIfPrimaryMultihashNotExists(
        primaryMultihash: Multihash,
        secondaryMultihash: Multihash,
        type: ObjectType,
        temp: Long
    ): ProtoMetaService.Entry {
        val entry = ProtoMetaService.Entry(
            primaryMultihash, secondaryMultihash, type, temp
        )
        return try {
            dynamoDbClient.putItem(
                PutItemRequest.builder()
                    .tableName(dynamoTableName)
                    .item(entry.toItem())
                    .conditionExpression("attribute_not_exists($primaryHashKeyName)")
                    .build()
            )
            entry
        } catch (e: ConditionalCheckFailedException) {
            // At this time, we know the key is already exists
            // The important data is immutable by design (only temp is mutable)
            // It's ok to not use transaction to get the existing value
            getByPrimaryMultihash(primaryMultihash)
                ?: throw IllegalStateException("Entry exists but not found: ${primaryMultihash.toBase58()}")
        }
    }

    override fun deleteByPrimaryMultihash(primaryMultihash: Multihash): ProtoMetaService.Entry? {
        val resp = dynamoDbClient.deleteItem(
            DeleteItemRequest.builder()
                .tableName(dynamoTableName)
                .key(primaryMultihash.toKey())
                .returnValues(ReturnValue.ALL_OLD)
                .build()
        )
        return if (resp.hasAttributes())
            resp.attributes().toEntry()
        else
            null
    }

    override fun close() = Unit
}
