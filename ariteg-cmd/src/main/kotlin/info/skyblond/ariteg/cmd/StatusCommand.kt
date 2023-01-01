package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.obj.Link
import info.skyblond.ariteg.storage.obj.ListObject
import info.skyblond.ariteg.storage.obj.TreeObject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class StatusCommand : CliktCommand(
    name = "status",
    help = "Perform a full object scan and show some status"
) {
    private val logger = KotlinLogging.logger("Status")
    override fun run(): Unit = runBlocking {
        val entries = Operations.listEntry(CmdContext.storage)
        val pureBlobSize = AtomicReference(BigInteger.ZERO)
        val representedSize = AtomicReference(BigInteger.ZERO)
        val blobs = ConcurrentHashMap<String, Long>()
        val lists = ConcurrentHashMap<String, Long>()
        val trees = ConcurrentHashMap<String, Long>()

        val jobCounter = AtomicLong(0)

        logger.info { "Scanning objects, this make takes a lot of time and I/O ops...." }
        val countObjFuture = async {
            Triple(
                CmdContext.storage.listObjects(Link.Type.BLOB).count(),
                CmdContext.storage.listObjects(Link.Type.LIST).count(),
                CmdContext.storage.listObjects(Link.Type.TREE).count(),
            )
        }

        val linkChannel = Channel<Link>(Int.MAX_VALUE)
        // create workers
        repeat(Runtime.getRuntime().availableProcessors() * 2) {
            launch {
                for (link in linkChannel) {
                    // read the link
                    val objFuture = async {
                        if (link.type != Link.Type.BLOB)
                            CmdContext.storage.read(link)
                        else null
                    }
                    // update counter
                    var shouldCountPureSize = false
                    when (link.type) {
                        Link.Type.BLOB -> blobs
                        Link.Type.LIST -> lists
                        Link.Type.TREE -> trees
                    }.compute(link.hash) { _, v ->
                        // add 1, or start with 1
                        // allow counting pure size on new blob
                        v?.plus(1) ?: 1L.also { shouldCountPureSize = true }
                    }
                    // get the result and handle sub links
                    when (link.type) {
                        Link.Type.BLOB -> emptyList<Link>()
                            .also {
                                representedSize.updateAndGet {
                                    it.add(link.size.toBigInteger())
                                }
                                if (shouldCountPureSize)
                                    pureBlobSize.updateAndGet {
                                        it.add(link.size.toBigInteger())
                                    }
                            }

                        Link.Type.LIST -> (objFuture.await() as ListObject).content
                        Link.Type.TREE -> (objFuture.await() as TreeObject).content.map { it.value }
                    }.forEach { jobCounter.incrementAndGet();linkChannel.send(it) }
                    // finished job
                    jobCounter.decrementAndGet()
                }
            }
        }

        entries.forEach { entry ->
            jobCounter.incrementAndGet()
            linkChannel.send(entry.root)
        }
        loop@ while (jobCounter.get() != 0L) {
            logger.info { "Remain links: ${jobCounter.get()}" }
            // print every 2min, but check each second
            for (i in 1..120) {
                delay(1000)
                if (jobCounter.get() == 0L)
                    break@loop
            }
        }
        // close channel, so workers can exit
        linkChannel.close()
        logger.info { "Analyzing result..." }
        val (blobCount, listCount, treeCount) = countObjFuture.await()

        // print the result
        echo(
            "\n-------------------- Status --------------------\n" +
                    "Total entries: ${entries.count()}\n\n" +
                    "Type: Total/Unused/Reused\n" +
                    "Blobs: ${blobCount}/${blobCount - blobs.size}/${blobs.count { it.value > 1 }}\n" +
                    "Lists: ${listCount}/${listCount - lists.size}/${lists.count { it.value > 1 }}\n" +
                    "Trees: ${treeCount}/${treeCount - trees.size}/${trees.count { it.value > 1 }}\n" +
                    "\nReachable blobs:  ${FileUtils.byteCountToDisplaySize(pureBlobSize.get())} (${pureBlobSize.get()} bytes)\n" +
                    "Represented size: ${FileUtils.byteCountToDisplaySize(representedSize.get())} (${representedSize.get()} bytes)\n"
        )
    }
}
