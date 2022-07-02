package info.skyblond.ariteg

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.*
import java.util.concurrent.CompletableFuture

interface AritegObject {
    @JsonIgnore
    fun getHashString(): CompletableFuture<String>

    fun verify(hash: String): CompletableFuture<Boolean> = CompletableFuture.supplyAsync {
        val realHash = getHashString()
        realHash.get() == hash
    }

    fun encodeToBytes(): ByteArray
}

abstract class AbstractAritegObject : AritegObject {
    protected fun calculateHash(data: ByteArray): CompletableFuture<String> = CompletableFuture.supplyAsync {
        val hash1 = CompletableFuture.supplyAsync { calculateHash1(data) }
        val hash2 = CompletableFuture.supplyAsync { calculateHash2(data) }
        hash1.get().toBase58() + "," + hash2.get().toBase58()
    }

    override fun getHashString(): CompletableFuture<String> = calculateHash(encodeToBytes())
}

data class Blob(val data: ByteArray) : AbstractAritegObject() {
    override fun encodeToBytes(): ByteArray = data

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Blob) return false

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Link(
    val hash: String,
    val type: Type,
    val name: String? = null
) {
    enum class Type {
        BLOB, LIST, TREE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Link) return false

        if (hash != other.hash) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

}

private val objectMapper = jacksonObjectMapper().also {
    // Use ISO8601 as
    it.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    it.dateFormat = StdDateFormat().withColonInTimeZone(true)
}

data class ListObject(
    val content: List<Link>
) : AbstractAritegObject() {

    init {
        content.forEach { require(it.name == null) { "Links in list node must not have name" } }
    }

    fun toJson(): String = objectMapper.writeValueAsString(this)

    override fun encodeToBytes(): ByteArray = toJson().encodeToByteArray()

    companion object {
        fun fromJson(json: String): ListObject = objectMapper.readValue(json)
    }

}

data class TreeObject(
    val content: List<Link>
) : AbstractAritegObject() {
    init {
        content.forEach { require(it.name != null) { "Links in tree node must have name" } }
    }

    fun toJson(): String = objectMapper.writeValueAsString(this)

    override fun encodeToBytes(): ByteArray = toJson().encodeToByteArray()

    companion object {
        fun fromJson(json: String): TreeObject = objectMapper.readValue(json)
    }

    override fun getHashString(): CompletableFuture<String> = calculateHash(toString().encodeToByteArray())
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Entry(
    val name: String,
    val link: Link,
    val time: Date,
    val note: String? = null
) {
    fun toJson(): String = objectMapper.writeValueAsString(this)

    companion object {
        fun fromJson(json: String): Entry = objectMapper.readValue(json)
    }
}
