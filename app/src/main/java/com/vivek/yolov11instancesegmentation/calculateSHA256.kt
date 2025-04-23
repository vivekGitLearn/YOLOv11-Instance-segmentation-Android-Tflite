import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun calculateSHA256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(4096)
    FileInputStream(file).use { fis ->
        var bytesRead = fis.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = fis.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
