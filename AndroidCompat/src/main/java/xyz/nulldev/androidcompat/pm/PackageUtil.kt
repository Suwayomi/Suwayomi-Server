package xyz.nulldev.androidcompat.pm

import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import net.dongliu.apk.parser.bean.ApkMeta
import java.io.File

fun ApkMeta.toPackageInfo(apk: File): PackageInfo =
    PackageInfo().also {
        it.packageName = packageName
        // ApkMeta.versionCode is a Long; android.content.pm.PackageInfo
        // exposes an int. Clamp instead of silently truncating (or throwing)
        // on APKs declaring a version code outside the int range.
        it.versionCode = versionCode.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        it.versionName = versionName

        it.reqFeatures =
            usesFeatures
                ?.map { feature ->
                    FeatureInfo().apply {
                        name = feature.name
                    }
                }?.toTypedArray()
                ?: emptyArray()

        it.applicationInfo =
            ApplicationInfo().apply {
                packageName = it.packageName
                nonLocalizedLabel = label
                // The APK lives at <packageRoot>/package.apk, so the parent
                // directory is the package's private data directory.
                sourceDir = apk.absolutePath
                publicSourceDir = apk.absolutePath
                dataDir = apk.parentFile?.absolutePath ?: apk.absolutePath
            }
    }
