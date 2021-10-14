package info.skyblond.ariteg.multihash

import io.ipfs.multihash.Multihash
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A kotlinx serializer for [Multihash]. It delegates the logic to [ByteArraySerializer].
 * */
object MultihashSerializer : KSerializer<Multihash> {
    private val delegateSerializer = ByteArraySerializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("Multihash", delegateSerializer.descriptor)

    override fun deserialize(decoder: Decoder): Multihash {
        return Multihash.deserialize(delegateSerializer.deserialize(decoder))
    }

    override fun serialize(encoder: Encoder, value: Multihash) {
        delegateSerializer.serialize(encoder, value.toBytes())
    }
}
