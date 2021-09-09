package info.skyblond.ariteg.objects

import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.AritegObjectType

abstract class AbstractAritegObject {
    /**
     * Convert current instance to proto
     * */
    abstract fun toProto(): AritegObject

    // If bytes are same, then they are same
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractAritegObject) return false

        val thisBytes = this.toProto().toByteArray()
        val otherBytes = this.toProto().toByteArray()

        if (!thisBytes.contentEquals(otherBytes)) return false

        return true
    }

    // hash is the byte content
    override fun hashCode(): Int {
        return this.toProto().toByteArray().contentHashCode()
    }
}

abstract class AbstractAritegObjectCompanion<T>(
    protected val expectedType: AritegObjectType
) where T : AbstractAritegObject {

    /**
     * If the object is type
     * */
    fun isTypeOf(proto: AritegObject): Boolean {
        return proto.typeOfObject == AritegObjectType.BLOB
    }

    /**
     * Generate instance from proto.
     * */
    fun fromProto(proto: AritegObject): T {
        require(isTypeOf(proto)) { "Expect ${expectedType.name} object but ${proto.typeOfObject.name} object is given" }
        return toInstance(proto)
    }

    protected abstract fun toInstance(proto: AritegObject): T
}
