package info.skyblond.ariteg.storage

import info.skyblond.ariteg.*
import java.util.*
import java.util.concurrent.CompletableFuture

abstract class AbstractStorage : Storage {

    protected abstract val key: ByteArray?

    protected fun getData(data: ByteArray): ByteArray =
        if (key != null) encrypt(key!!, data) else data

    protected fun parseData(data: ByteArray): ByteArray =
        if (key != null) decrypt(key!!, data) else data

    override fun write(type: Link.Type, obj: AritegObject): CompletableFuture<Link> = when (type) {
        Link.Type.BLOB -> writeBlob(obj as Blob)
        Link.Type.LIST -> writeList(obj as ListObject)
        Link.Type.TREE -> writeTree(obj as TreeObject)
    }

    abstract fun writeBlob(blob: Blob): CompletableFuture<Link>
    abstract fun writeList(listObj: ListObject): CompletableFuture<Link>
    abstract fun writeTree(treeObj: TreeObject): CompletableFuture<Link>

    override fun read(link: Link): CompletableFuture<out AritegObject> = when (link.type) {
        Link.Type.BLOB -> readBlob(link)
        Link.Type.LIST -> readList(link)
        Link.Type.TREE -> readTree(link)
    }

    abstract fun readBlob(link: Link): CompletableFuture<Blob>
    abstract fun readList(link: Link): CompletableFuture<ListObject>
    abstract fun readTree(link: Link): CompletableFuture<TreeObject>

    override fun delete(link: Link): CompletableFuture<Void> = when (link.type) {
        Link.Type.BLOB -> deleteBlob(link)
        Link.Type.LIST -> deleteList(link)
        Link.Type.TREE -> deleteTree(link)
    }

    abstract fun deleteBlob(link: Link): CompletableFuture<Void>
    abstract fun deleteList(link: Link): CompletableFuture<Void>
    abstract fun deleteTree(link: Link): CompletableFuture<Void>

    override fun resolve(rootLink: Link): CompletableFuture<Set<Link>> = CompletableFuture.supplyAsync {
        val queue = LinkedList(listOf(rootLink))
        val result = HashSet<Link>()
        val futureList = LinkedList<CompletableFuture<out AritegObject>>()

        while (queue.isNotEmpty()) {
            val link = queue.pop()
            when (link.type) {
                Link.Type.BLOB -> result.add(link)
                else -> futureList.add(read(link))
            }
            if (queue.isEmpty()) {
                futureList.forEach {
                    when (val obj = it.get()) {
                        is ListObject -> {
                            queue.addAll(obj.content)
                        }
                        is TreeObject -> {
                            queue.addAll(obj.content)
                        }
                    }
                }
                futureList.clear()
            }
        }

        result
    }

    protected fun base64Encode(name: String): String =
        Base64.getUrlEncoder().encodeToString(name.encodeToByteArray())
}
