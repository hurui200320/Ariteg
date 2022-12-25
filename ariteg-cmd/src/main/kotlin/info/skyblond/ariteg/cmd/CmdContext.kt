package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Link
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.Storage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogger
import java.io.File

object CmdContext {
    @JvmStatic
    lateinit var storage: Storage
        private set

    @JvmStatic
    lateinit var logger: KLogger
        private set

    @JvmStatic
    @Suppress("MemberVisibilityCanBePrivate")
    val slicer
        get() = getSlicer()

    @JvmStatic
    fun setStorage(storage: Storage) {
        CmdContext.storage = storage
    }

    fun setLogger(logger: KLogger) {
        CmdContext.logger = logger
    }

    @JvmStatic
    fun download(ids: List<String>, rootFolder: File) {
        logger.info { "Finding entry..." }
        val downloaded = mutableSetOf<String>()
        runBlocking {
            Operations.listEntry(storage)
                .forEach {
                    if (it.id in ids) {
                        logger.info { "Start downloading ${it.id}..." }
                        Operations.restore(it, storage, rootFolder)
                        logger.info { "Done" }
                        downloaded.add(it.id)
                    }
                }
        }
        val notFound = (ids - downloaded)
        if (notFound.isNotEmpty()) {
            error("Entry not found: $notFound")
        }
    }

    @JvmStatic
    fun gc() {
        logger.info { "Starting gc..." }
        Operations.gc(storage)
        logger.info { "Done" }
    }

    @JvmStatic
    fun integrityCheck(deleting: Boolean) {
        logger.info { "Starting integrity check..." }
        Operations.integrityCheck(storage, deleting)
        logger.info { "Done" }
    }

    @JvmStatic
    fun listEntry(): Sequence<Entry> {
        return Operations.listEntry(storage)
    }

    @JvmStatic
    fun removeEntry(ids: List<String>) {
        logger.info { "Listing entries..." }
        val entries = Operations.listEntry(storage)
        logger.info { "Deleting..." }

        ids.forEach { target ->
            entries.find { it.id == target }?.let { entry ->
                Operations.deleteEntry(entry, storage)
                logger.info { "Entry $target deleted" }
            } ?: logger.error { "Entry $target not found" }
        }
    }

    @JvmStatic
    fun upload(files: List<File>) {
        runBlocking {
            files.map { file ->
                logger.info { "Uploading ${file.canonicalPath}" }
                val entry = Operations.digest(file, slicer, storage)
                logger.info { "Finished ${entry.name}" }
                entry
            }.forEach { entry ->
                entry.printDetails()
            }
        }

        logger.info { "Done" }
    }

    @JvmStatic
    fun verifyEntry(ids: List<String>) {
        logger.info { "Listing entries..." }
        Operations.listEntry(storage).forEach { entry ->
            if (ids.isNotEmpty() && entry.id !in ids) {
                return@forEach
            }
            runBlocking {
                logger.info { "Verifying entry: ${entry.id} (${entry.name})" }
                val blobs = Operations.resolve(entry, storage)
                logger.info { "Verifying ${blobs.size / 1000.0}K blobs..." }

                val taskChannel = Channel<Pair<Int, Link>>(Channel.UNLIMITED)

                repeat(Runtime.getRuntime().availableProcessors() * 2) {
                    launch {
                        for (task in taskChannel) {
                            storage.read(task.second)
                            if (task.first % 1000 == 0) {
                                logger.info { "${task.first / 1000}K blobs are checked" }
                            }
                        }
                    }
                }

                blobs.forEachIndexed { index, link ->
                    taskChannel.send(index to link)
                }
                taskChannel.close()
            }
        }
        logger.info { "Done" }
    }

}
