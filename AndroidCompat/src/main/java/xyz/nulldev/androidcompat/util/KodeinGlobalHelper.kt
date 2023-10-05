package xyz.nulldev.androidcompat.util

import android.content.Context
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import xyz.nulldev.androidcompat.androidimpl.CustomContext
import xyz.nulldev.androidcompat.androidimpl.FakePackageManager
import xyz.nulldev.androidcompat.info.ApplicationInfoImpl
import xyz.nulldev.androidcompat.io.AndroidFiles
import xyz.nulldev.androidcompat.pm.PackageController
import xyz.nulldev.androidcompat.service.ServiceSupport

/**
 * Helper class to allow access to Kodein from Java
 */
object KodeinGlobalHelper {
    /**
     * Get the Kodein object
     */
    @JvmStatic
    fun kodein() = DI.global

    /**
     * Get a dependency
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> instance(
        type: Class<T>,
        kodein: DI? = null,
    ): T {
        return when (type) {
            AndroidFiles::class.java -> {
                val instance: AndroidFiles by (kodein ?: kodein()).instance()
                instance as T
            }
            ApplicationInfoImpl::class.java -> {
                val instance: ApplicationInfoImpl by (kodein ?: kodein()).instance()
                instance as T
            }
            ServiceSupport::class.java -> {
                val instance: ServiceSupport by (kodein ?: kodein()).instance()
                instance as T
            }
            FakePackageManager::class.java -> {
                val instance: FakePackageManager by (kodein ?: kodein()).instance()
                instance as T
            }
            PackageController::class.java -> {
                val instance: PackageController by (kodein ?: kodein()).instance()
                instance as T
            }
            CustomContext::class.java -> {
                val instance: CustomContext by (kodein ?: kodein()).instance()
                instance as T
            }
            Context::class.java -> {
                val instance: Context by (kodein ?: kodein()).instance()
                instance as T
            }
            else -> throw IllegalArgumentException("Kodein instance not found")
        }
    }

    @JvmStatic
    fun <T : Any> instance(type: Class<T>): T {
        return instance(type, null)
    }
}
