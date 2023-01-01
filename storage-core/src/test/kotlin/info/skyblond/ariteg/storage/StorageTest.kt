package info.skyblond.ariteg.storage

import kotlin.random.Random

internal class PlainStorageTest: AbstractStorageTest() {
    override val storage: InMemoryStorage = InMemoryStorage(null)
}

internal class EncryptedStorageTest: AbstractStorageTest() {
    override val storage: InMemoryStorage = InMemoryStorage(Random.nextBytes(32))
}
