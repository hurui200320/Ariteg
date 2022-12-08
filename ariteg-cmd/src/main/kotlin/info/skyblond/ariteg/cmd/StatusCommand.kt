package info.skyblond.ariteg.cmd

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.ariteg.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class StatusCommand : CliktCommand(
    name = "status",
    help = "Perform a full object scan and show some status"
), CoroutineScope {

    override fun run() {
        CmdContext.setLogger(KotlinLogging.logger("Status"))

        val entries = Operations.listEntry(CmdContext.storage)
        val pureBlobSize = AtomicReference(BigInteger.ZERO)
        val representedSize = AtomicReference(BigInteger.ZERO)
        val blobs = ConcurrentHashMap<String, Long>()
        val lists = ConcurrentHashMap<String, Long>()
        val trees = ConcurrentHashMap<String, Long>()

        val jobCounter = AtomicLong(0)

        CmdContext.logger.info { "Scanning objects, this make takes a lot of time and I/O ops...." }
        val listObjFuture = CmdContext.storage.listObjects()

        val linkChannel = Channel<Link>(Int.MAX_VALUE)
        // create workers
        repeat(Runtime.getRuntime().availableProcessors()) {
            launch {
                for (link in linkChannel) {
                    // read the link
                    val objFuture = CmdContext.storage.read(link)
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
                    val obj = withContext(Dispatchers.IO) {
                        objFuture.get()
                    }
                    when (link.type) {
                        Link.Type.BLOB -> emptyList<Link>()
                            .also {
                                representedSize.updateAndGet {
                                    it.add((obj as Blob).data.size.toBigInteger())
                                }
                                if (shouldCountPureSize)
                                    pureBlobSize.updateAndGet {
                                        it.add((obj as Blob).data.size.toBigInteger())
                                    }
                            }

                        Link.Type.LIST -> (obj as ListObject).content
                        Link.Type.TREE -> (obj as TreeObject).content
                    }.forEach { jobCounter.incrementAndGet();linkChannel.send(it) }
                    // finished job
                    jobCounter.decrementAndGet()
                }
            }
        }

        runBlocking {
            entries.forEach { entry ->
                jobCounter.incrementAndGet()
                linkChannel.send(entry.link)
            }
            loop@ while (jobCounter.get() != 0L) {
                CmdContext.logger.info { "Remain links: ${jobCounter.get()}" }
                // print every 2min, but check each second
                for (i in 1..120) {
                    delay(1000)
                    if (jobCounter.get() == 0L)
                        break@loop
                }
            }
            // close channel, so workers can exit
            linkChannel.close()
        }
        CmdContext.logger.info { "Analyzing result..." }
        val (blobSet, listSet, treeSet) = listObjFuture.get()

        // print the result
        CmdContext.logger.info {
            "\n-------------------- Status --------------------\n" +
                    "Total entries: ${entries.size}\n\n" +
                    "Type: Total/Unused/Reused\n" +
                    "Blobs: ${blobSet.size}/${blobSet.size - blobs.size}/${blobs.count { it.value > 1 }}\n" +
                    "Lists: ${listSet.size}/${listSet.size - lists.size}/${lists.count { it.value > 1 }}\n" +
                    "Trees: ${treeSet.size}/${treeSet.size - trees.size}/${trees.count { it.value > 1 }}\n" +
                    "\nPure blob size: ${FileUtils.byteCountToDisplaySize(pureBlobSize.get())} (${pureBlobSize.get()} bytes)\n" +
                    "Represented size: ${FileUtils.byteCountToDisplaySize(representedSize.get())} (${representedSize.get()} bytes)\n"
        }
    }


    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job
}
