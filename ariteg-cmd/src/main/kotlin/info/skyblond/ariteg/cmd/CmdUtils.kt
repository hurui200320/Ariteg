package info.skyblond.ariteg.cmd

import info.skyblond.ariteg.storage.FileStorage
import info.skyblond.ariteg.storage.MinioStorage
import io.minio.MinioClient
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*

@Suppress("unused")
object CmdUtils {

    @JvmStatic
    fun createFile(path: String): File = File(path).also { FileUtils.forceMkdirParent(it) }

    @JvmStatic
    fun createFile(file: File, name: String): File = File(file, name)

    @JvmStatic
    fun createFolder(path: String): File = File(path).also { FileUtils.forceMkdir(it) }

    @JvmStatic
    fun deleteFile(file: File) = FileUtils.forceDelete(file)

    @JvmStatic
    fun setDeleteFileOnExit(file: File) = FileUtils.forceDeleteOnExit(file)

    @JvmStatic
    fun createFileStorage(file: File, key: String): FileStorage =
        FileStorage(file, if (key.isBlank()) null else Base64.getDecoder().decode(key))

    @JvmStatic
    fun createMinioStorage(
        host: String, accessKey: String, secretKey: String,
        bucketName: String, key: String
    ): MinioStorage = MinioStorage(
        MinioClient.builder()
            .endpoint(host)
            .credentials(accessKey, secretKey)
            .build(),
        bucketName,
        if (key.isBlank()) null else Base64.getDecoder().decode(key)
    )
}
