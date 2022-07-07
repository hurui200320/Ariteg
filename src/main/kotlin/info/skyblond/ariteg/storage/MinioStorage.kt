package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import io.minio.*
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture


class MinioStorage(
    private val minioClient: MinioClient,
    private val bucketName: String,
    override val key: ByteArray? = null
) : AbstractStorage() {
    private val logger = KotlinLogging.logger("S3Storage")

    init {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
        }
        logger.info { "Using bucket: $bucketName" }

        if (key != null) {
            require(key.size == 32) { "The key must be 256bits" }
        }
    }

    private fun getKey(type: String, name: String): String = "${type.lowercase()}/$name"

    private fun internalWrite(type: Link.Type, obj: AritegObject): CompletableFuture<Link> =
        CompletableFuture.supplyAsync {
            val hash = obj.getHashString().get()
            val key = getKey(type.name, hash)
            val data = getData(obj.encodeToBytes())

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(key)
                    .stream(data.inputStream(), data.size.toLong(), -1)
                    .build()
            )

            Link(hash, type)
        }

    override fun writeBlob(blob: Blob): CompletableFuture<Link> = internalWrite(Link.Type.BLOB, blob)

    override fun writeList(listObj: ListObject): CompletableFuture<Link> = internalWrite(Link.Type.LIST, listObj)

    override fun writeTree(treeObj: TreeObject): CompletableFuture<Link> = internalWrite(Link.Type.TREE, treeObj)

    private fun internalRead(link: Link): CompletableFuture<ByteArray> = CompletableFuture.supplyAsync {
        val key = getKey(link.type.name, link.hash)
        val content = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .`object`(key)
                .build()
        ).use { it.readAllBytes() }
        parseData(content)
    }

    override fun readBlob(link: Link): CompletableFuture<Blob> =
        internalRead(link).thenApply { data ->
            Blob(data).also { it.verify(link.hash) }
        }

    override fun readList(link: Link): CompletableFuture<ListObject> =
        internalRead(link).thenApply { data ->
            val json = data.decodeToString()
            ListObject.fromJson(json).also { it.verify(link.hash) }
        }

    override fun readTree(link: Link): CompletableFuture<TreeObject> =
        internalRead(link).thenApply { data ->
            val json = data.decodeToString()
            TreeObject.fromJson(json).also { it.verify(link.hash) }
        }

    private fun internalDelete(link: Link): CompletableFuture<Void> = CompletableFuture.runAsync {
        val key = getKey(link.type.name, link.hash)
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .`object`(key)
                .build()
        )
    }

    override fun deleteBlob(link: Link): CompletableFuture<Void> = internalDelete(link)

    override fun deleteList(link: Link): CompletableFuture<Void> = internalDelete(link)

    override fun deleteTree(link: Link): CompletableFuture<Void> = internalDelete(link)

    private fun internalListObjects(type: Link.Type): CompletableFuture<Set<String>> = CompletableFuture.supplyAsync {
        val prefix = "${type.name.lowercase()}/"
        val keys = HashSet<String>()
        var startAfter = ""
        while (true) {
            val resp = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .startAfter(startAfter)
                    .build()
            )
            // get path
            val batch = resp.map { it.get().objectName() }
            // get hash, aka, the name. not the full path
            keys.addAll(batch.map { it.removePrefix(prefix) })
            if (batch.isNotEmpty()) {
                // not finished, keep getting keys
                startAfter = batch.last()
            } else {
                // finished, exit loop
                break
            }
        }
        keys
    }

    override fun listObjects(): CompletableFuture<Triple<Set<String>, Set<String>, Set<String>>> =
        internalListObjects(Link.Type.BLOB)
            .thenCombine(internalListObjects(Link.Type.LIST)) { b, l -> b to l }
            .thenCombine(internalListObjects(Link.Type.TREE)) { (b, l), t -> Triple(b, l, t) }

    override fun addEntry(entry: Entry): CompletableFuture<Entry> = CompletableFuture.supplyAsync {
        val key = getKey("entry", base64Encode(entry.name))
        val data = getData(entry.toJson().encodeToByteArray())

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(key)
                .stream(data.inputStream(), data.size.toLong(), -1)
                .build()
        )

        entry
    }

    override fun removeEntry(entry: Entry): CompletableFuture<Void> = CompletableFuture.runAsync {
        val key = getKey("entry", base64Encode(entry.name))
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .`object`(key)
                .build()
        )
    }

    override fun listEntry(): Iterable<Entry> {
        val prefix = "entry/"
        val paths = mutableListOf<String>()
        var startAfter = ""
        while (true) {
            val resp = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .startAfter(startAfter)
                    .build()
            )
            // get path
            val batch = resp.map { it.get().objectName() }
            // get hash, aka, the name. not the full path
            paths.addAll(batch)
            if (batch.isNotEmpty()) {
                // not finished, keep getting keys
                startAfter = batch.last()
            } else {
                // finished, exit loop
                break
            }
        }

        return object : Iterable<Entry> {
            override fun iterator(): Iterator<Entry> {
                return object : Iterator<Entry> {
                    private var pointer = 0

                    override fun hasNext(): Boolean = pointer < paths.size

                    override fun next(): Entry {
                        if (!hasNext()) throw NoSuchElementException("No next elements")

                        val json = parseData(minioClient.getObject(
                            GetObjectArgs.builder()
                                .bucket(bucketName)
                                .`object`(paths[pointer++])
                                .build()
                        ).use { it.readAllBytes() }).decodeToString()

                        return Entry.fromJson(json)
                    }
                }
            }
        }
    }

    override fun close() {
        // nop
    }
}
