package ir.armor.tachidesk.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

fun writeStream(fileStream: InputStream, path: String) {
    Files.newOutputStream(Paths.get(path)).use { os ->
        val buffer = ByteArray(1024)
        var len: Int
        while (fileStream.read(buffer).also { len = it } > 0) {
            os.write(buffer, 0, len)
        }
    }
}

fun pathToInputStream(path: String): InputStream {
    return BufferedInputStream(FileInputStream(path))
}

fun findFileNameStartingWith(directoryPath: String, fileName: String): String? {
    File(directoryPath).listFiles().forEach { file ->
        if (file.name.startsWith(fileName))
            return "$directoryPath/${file.name}"
    }
    return null
}
