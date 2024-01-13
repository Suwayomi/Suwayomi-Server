package xyz.nulldev.androidcompat.pm

import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import net.dongliu.apk.parser.bean.ApkMeta
import java.io.File

fun ApkMeta.toPackageInfo(apk: File): PackageInfo {
    return PackageInfo().also {
        it.packageName = packageName
        it.versionCode = versionCode.toInt()
        it.versionName = versionName

        it.reqFeatures =
            usesFeatures.map {
                FeatureInfo().apply {
                    name = it.name
                }
            }.toTypedArray()

        it.applicationInfo =
            ApplicationInfo().apply {
                packageName = it.packageName
                nonLocalizedLabel = label
                sourceDir = apk.absolutePath
            }
    }
}
