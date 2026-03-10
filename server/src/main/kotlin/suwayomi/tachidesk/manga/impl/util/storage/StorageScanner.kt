package suwayomi.tachidesk.manga.impl.util.storage

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class StorageScanner(private val fileSystem: FileSystem = FileSystem.SYSTEM) {
    /**
     * Devuelve un par con (Tamaño de carpeta, Espacio disponible en disco) en Bytes.
     */
    fun getDirectoryStats(directoryPath: String): DirectoryStats {
        val path = directoryPath.toPath()
        val size = calculateSize(path)
        val available = getAvailableDiskSpace(path)

        return DirectoryStats(
            folderSize = size,
            folderSizePretty = formatBytes(size),
            availableSpace = available,
            availableSpacePretty = formatBytes(available)
        )
    }

    fun getFolderSize(directoryPath: String): Long = calculateSize(directoryPath.toPath())

    fun getFolderSizePretty(directoryPath: String): String = formatBytes(getFolderSize(directoryPath))

    private fun calculateSize(path: Path): Long {
        return try {
            fileSystem.listRecursively(path).sumOf { file ->
                // metadataOrNull es más rápido que try-catch para archivos sin acceso
                fileSystem.metadataOrNull(file)?.size ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun getAvailableDiskSpace(path: Path): Long {
        return try {
            path.toFile().usableSpace
        } catch (e: Exception) {
            -1L
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "Desconocido"
        if (bytes < 1024) return "$bytes B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceAtMost(units.size - 1)
        val size = bytes / Math.pow(1024.0, exp.toDouble())

        return "%.2f %s".format(size, units[exp])
    }
}

data class DirectoryStats(
    val folderSize: Long,
    val folderSizePretty: String,
    val availableSpace: Long,
    val availableSpacePretty: String
)
