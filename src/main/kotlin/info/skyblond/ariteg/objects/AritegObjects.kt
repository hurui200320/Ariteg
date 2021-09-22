package info.skyblond.ariteg.objects

import com.google.protobuf.ByteString
import info.skyblond.ariteg.AritegLink
import info.skyblond.ariteg.AritegObject
import info.skyblond.ariteg.CommitData
import info.skyblond.ariteg.ObjectType
import javax.annotation.concurrent.Immutable

/**
 * Blob Object, store data only.
 * */
@Immutable
data class BlobObject(
    val data: ByteString
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<BlobObject>(ObjectType.BLOB) {
        override fun toInstance(proto: AritegObject): BlobObject {
            return BlobObject(proto.data)
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setType(ObjectType.BLOB)
            .setData(data)
            .build()
    }
}

/**
 * List Object, concat list of blob/list to get data.
 * */
@Immutable
data class ListObject(
    val list: List<AritegLink>
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<ListObject>(ObjectType.LIST) {
        override fun toInstance(proto: AritegObject): ListObject {
            return ListObject(proto.linksList)
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setType(ObjectType.LIST)
            .addAllLinks(list)
            .build()
    }
}

/**
 * Tree Object, represent a folder-like structure with links.
 * */
@Immutable
data class TreeObject(
    val links: List<AritegLink>
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<TreeObject>(ObjectType.TREE) {
        override fun toInstance(proto: AritegObject): TreeObject {
            return TreeObject(proto.linksList)
        }
    }

    override fun toProto(): AritegObject {
        return AritegObject.newBuilder()
            .setType(ObjectType.TREE)
            .addAllLinks(links)
            .build()
    }
}

/**
 * Commit Object, representing a snapshot of an object at some time.
 * */
@Immutable
data class CommitObject(
    val type: ObjectType,
    val unixTimestamp: Long,
    val message: String,
    val parentLink: AritegLink,
    val commitObjectLink: AritegLink,
    val authorLink: AritegLink,
) : AbstractAritegObject() {
    companion object : AbstractAritegObjectCompanion<CommitObject>(ObjectType.COMMIT) {
        override fun toInstance(proto: AritegObject): CommitObject {
            return CommitObject(
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
            .setType(ObjectType.COMMIT)
            .setCommitData(
                CommitData.newBuilder()
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
