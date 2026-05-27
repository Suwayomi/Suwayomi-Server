package suwayomi.tachidesk.manga.impl.util.storage

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.pow

class StorageScanner(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private companion object {
        const val CACHE_TTL_MS = 60000L
    }

    private val sizeCache = ConcurrentHashMap<String, Long>()
    private val cacheTimestamp = ConcurrentHashMap<String, Long>()

    /**
     * Invalida la caché de una ruta específica cuando termine una descarga
     * para forzar un nuevo cálculo real la próxima vez.
     */
    fun invalidateCache(directoryPath: String) {
        sizeCache.remove(directoryPath)
        cacheTimestamp.remove(directoryPath)
    }

    /**
     * Limpia por completo la caché de almacenamiento.
     */
    fun clearCache() {
        sizeCache.clear()
        cacheTimestamp.clear()
    }

    /**
     * Devuelve un par con (Tamaño de carpeta, Espacio disponible en disco) en Bytes.
     */
    fun getDirectoryStats(directoryPath: String): DirectoryStats {
        val path = directoryPath.toPath()
        val size = getFolderSize(directoryPath) // Utiliza la caché interna
        val available = getAvailableDiskSpace(path)

        return DirectoryStats(
            folderSize = size,
            folderSizePretty = formatBytes(size),
            availableSpace = available,
            availableSpacePretty = formatBytes(available),
        )
    }

    /**
     * Obtiene el tamaño de la carpeta respetando la caché de tiempo.
     */
    fun getFolderSize(directoryPath: String): Long {
        val now = System.currentTimeMillis()
        val lastCheck = cacheTimestamp[directoryPath] ?: 0L

        if (sizeCache.containsKey(directoryPath) && (now - lastCheck) < CACHE_TTL_MS) {
            return sizeCache[directoryPath] ?: 0L
        }

        val freshSize = calculateSize(directoryPath.toPath())
        sizeCache[directoryPath] = freshSize
        cacheTimestamp[directoryPath] = now

        return freshSize
    }

    fun getFolderSizePretty(directoryPath: String): String = formatBytes(getFolderSize(directoryPath))

    private fun calculateSize(path: Path): Long =
        try {
            fileSystem.listRecursively(path).sumOf { file ->
                fileSystem.metadataOrNull(file)?.size ?: 0L
            }
        } catch (e: Exception) {
            0L
        }

    private fun getAvailableDiskSpace(path: Path): Long =
        try {
            path.toNioPath().toFile().usableSpace
        } catch (e: Exception) {
            -1L
        }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "Desconocido"
        if (bytes < 1024) return "$bytes B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        val size = bytes / 1024.0.pow(exp.toDouble())

        return "%.2f %s".format(size, units[exp])
    }
}

data class DirectoryStats(
    val folderSize: Long,
    val folderSizePretty: String,
    val availableSpace: Long,
    val availableSpacePretty: String,
)
