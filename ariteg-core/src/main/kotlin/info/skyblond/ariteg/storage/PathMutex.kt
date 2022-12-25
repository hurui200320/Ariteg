package info.skyblond.ariteg.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.absoluteValue

object PathMutex {
    private val locks = Array(96) { Mutex() }
// TODO dispatch to channel
    suspend fun <R> usePath(obj: Any, block: suspend () -> R): R =
        locks[obj.hashCode().absoluteValue % locks.size].withLock { block() }
}
