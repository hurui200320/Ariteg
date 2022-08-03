package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.storage.Storage

object Global {
    private val configMap = HashMap<String, Any>()

    private const val KEY_STORAGE = "storage"

    private fun set(key: String, obj: Any) {
        configMap[key] = obj
    }

    private fun <T> get(key: String): T {
        @Suppress("UNCHECKED_CAST")
        return configMap[key] as T
    }

    fun setStorage(storage: Storage) = set(KEY_STORAGE, storage)

    fun getStorage(): Storage = get(KEY_STORAGE)

}
