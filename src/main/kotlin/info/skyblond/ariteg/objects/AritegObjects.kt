package info.skyblond.ariteg.objects

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegCommitData
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.AritegObjectType

data class AritegBlobObject(
    val data: ByteString
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<AritegBlobObject>(AritegObjectType.BLOB) {
        override fun toInstance(proto: AritegObject): AritegBlobObject {
            return AritegBlobObject(proto.data)
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setTypeOfObject(AritegObjectType.BLOB)
            .setData(data)
            .build()
    }
}

data class AritegListObject(
    val list: List<Pair<AritegObjectType, AritegLink>>
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<AritegListObject>(AritegObjectType.LIST) {
        override fun toInstance(proto: AritegObject): AritegListObject {
            return AritegListObject(proto.typesOfLinksList.zip(proto.linksList))
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setTypeOfObject(AritegObjectType.LIST)
            .addAllTypesOfLinks(list.map { it.first })
            .addAllLinks(list.map { it.second })
            .build()
    }
}

data class AritegTreeObject(
    val list: List<Pair<AritegObjectType, AritegLink>>
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<AritegTreeObject>(AritegObjectType.TREE) {
        override fun toInstance(proto: AritegObject): AritegTreeObject {
            return AritegTreeObject(proto.typesOfLinksList.zip(proto.linksList))
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setTypeOfObject(AritegObjectType.TREE)
            .addAllTypesOfLinks(list.map { it.first })
            .addAllLinks(list.map { it.second })
            .build()
    }
}


data class AritegCommitObject(
    val type: AritegObjectType,
    val unixTimestamp: Long,
    val message: String,
    val parentLink: AritegLink,
    val commitObjectLink: AritegLink,
    val authorLink: AritegLink,
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<AritegCommitObject>(AritegObjectType.COMMIT) {
        override fun toInstance(proto: AritegObject): AritegCommitObject {
            return AritegCommitObject(
                type = proto.commitData.type,
                unixTimestamp = proto.commitData.unixTimestamp,
                message = proto.commitData.message,
                parentLink = proto.linksList.find { it.name == "parent" }!!,
                commitObjectLink = proto.linksList.find { it.name == "object" }!!,
                authorLink = proto.linksList.find { it.name == "author" }!!,
            )
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setTypeOfObject(AritegObjectType.COMMIT)
            .setCommitData(
                AritegCommitData.newBuilder()
                    .setType(type)
                    .setUnixTimestamp(unixTimestamp)
                    .setMessage(message)
                    .build()
            )
            .addLinks(parentLink)
            .addLinks(commitObjectLink)
            .addLinks(authorLink)
            .build()
    }
}


data class AritegEncryptedObject(
    val encryptedObjectBytes: ByteString,
    val tag: ByteString
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<AritegEncryptedObject>(AritegObjectType.ENCRYPTED) {
        override fun toInstance(proto: AritegObject): AritegEncryptedObject {
            return AritegEncryptedObject(
                proto.data,
                proto.encryptedTag
            )
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setTypeOfObject(AritegObjectType.ENCRYPTED)
            .setData(encryptedObjectBytes)
            .setEncryptedTag(tag)
            .build()
    }
}

data class AritegSignedObject(
    val signedObject: AritegObject,
    val signatures: List<ByteString>,
    val publicKeys: List<ByteString>
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<AritegSignedObject>(AritegObjectType.SIGNED) {
        override fun toInstance(proto: AritegObject): AritegSignedObject {
            return AritegSignedObject(
                AritegObject.parseFrom(proto.data),
                proto.signaturesList,
                proto.pubKeysList
            )
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setTypeOfObject(AritegObjectType.SIGNED)
            .setData(signedObject.toByteString())
            .addAllSignatures(signatures)
            .addAllPubKeys(publicKeys)
            .build()
    }
}
