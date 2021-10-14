package info.skyblond.ariteg.proto.storage

interface AsyncProtoStorageService : ProtoStorageService {
    /**
     * Return the count of current pending writing requests.
     * */
    fun getPendingWriteRequestCount(): Int
}
