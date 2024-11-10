package xyz.nulldev.androidcompat.util

import org.koin.core.Koin
import org.koin.mp.KoinPlatformTools

/**
 * Helper class to allow access to Kodein from Java
 */
object KoinGlobalHelper {
    /**
     * Get the Kodein object
     */
    @JvmStatic
    fun koin() = KoinPlatformTools.defaultContext().get()

    /**
     * Get a dependency
     */
    @JvmStatic
    fun <T : Any> instance(
        type: Class<T>,
        koin: Koin? = null,
    ): T = (koin ?: koin()).get(type.kotlin)

    @JvmStatic
    fun <T : Any> instance(type: Class<T>): T = instance(type, null)
}
