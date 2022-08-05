package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.Entry
import info.skyblond.ariteg.Operations
import info.skyblond.ariteg.storage.Storage
import mu.KLogger
import java.io.File
import java.util.concurrent.CompletableFuture

object CmdContext {
    lateinit var storage: Storage
        private set

    lateinit var logger: KLogger
        private set

    fun setStorage(storage: Storage) {
        this.storage = storage
    }

    fun setLogger(logger: KLogger) {
        this.logger = logger
    }

    fun download(id: String, rootFolder: File) {
        logger.info { "Finding entry..." }
        val entry = Operations.listEntry(storage)
            .find { it.id == id } ?: error("Entry not found")
        logger.info { "Start downloading..." }
        Operations.restore(entry, storage, rootFolder)
        logger.info { "Done" }
    }

    fun gc() {
        logger.info { "Starting gc..." }
        Operations.gc(storage)
        logger.info { "Done" }
    }

    fun integrityCheck() {
        logger.info { "Starting integrity check..." }
        Operations.integrityCheck(storage)
        logger.info { "Done" }
    }

    fun listEntry(): List<Entry> {
        return Operations.listEntry(storage)
    }

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

    fun upload(files: List<File>) {
        val slicerProvider = getSlicerProvider()

        files.map { file ->
            logger.info { "Uploading ${file.canonicalPath}" }
            val entry = Operations.digest(file, slicerProvider, storage)
            logger.info { "Finished ${entry.name}" }
            entry
        }.forEach { entry ->
            entry.printDetails()
        }

        logger.info { "Done" }
    }

    fun verifyEntry(ids: List<String>) {
        logger.info { "Listing entries..." }
        Operations.listEntry(storage).forEach { entry ->
            if (ids.isNotEmpty() && entry.id !in ids) {
                return@forEach
            }
            logger.info { "Verifying entry: ${entry.id} (${entry.name})" }
            val blobs = Operations.resolve(entry, storage)
            logger.info { "Verifying ${blobs.size / 1000.0}K blobs..." }
            val semaphore = Operations.getLimitingSemaphore()
            blobs.map {
                CompletableFuture.runAsync {
                    semaphore.acquire()
                    storage.read(it)
                }.exceptionally { semaphore.release(); throw it }
                    .thenRun { semaphore.release() }
            }.forEachIndexed { index, completableFuture ->
                completableFuture.get()
                if (index % 1000 == 0) {
                    logger.info { "${index / 1000}K blobs are checked" }
                }
            }
        }
        logger.info { "Done" }
    }

}
