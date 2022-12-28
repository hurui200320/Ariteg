package info.skyblond.ariteg.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.absoluteValue

internal object PathMutex {
    // use about 2MB mem when every single one is used
    private val locks = Array(1024) { lazy { Mutex() } }

    suspend fun <R> usePath(path: Any, block: suspend () -> R): R =
        locks[path.hashCode().absoluteValue % locks.size].value.withLock { block() }
}
