package suwayomi.tachidesk.manga.impl.util

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.io.InputStream

object AndroidManifestParser {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    @Serializable
    @XmlSerialName("manifest", "", "")
    data class AndroidManifest(
        @XmlSerialName("package", "", "")
        val packageName: String,
        @XmlSerialName("versionCode", ANDROID_NS, "android")
        val versionCode: Int? = null,
        @XmlSerialName("versionName", ANDROID_NS, "android")
        val versionName: String? = null,
        @XmlElement(true)
        @XmlSerialName("uses-sdk", "", "")
        val usesSdk: UsesSdk? = null,
        @XmlElement(true)
        @XmlSerialName("uses-feature", "", "")
        val usesFeatures: List<UsesFeature> = emptyList(),
        @XmlElement(true)
        @XmlSerialName("application", "", "")
        val application: Application? = null,
    )

    @Serializable
    @XmlSerialName("uses-sdk", "", "")
    data class UsesSdk(
        @XmlSerialName("minSdkVersion", ANDROID_NS, "android")
        val minSdkVersion: Int? = null,
        @XmlSerialName("targetSdkVersion", ANDROID_NS, "android")
        val targetSdkVersion: Int? = null,
    )

    @Serializable
    @XmlSerialName("uses-feature", "", "")
    data class UsesFeature(
        @XmlSerialName("name", ANDROID_NS, "android")
        val name: String? = null,
    )

    @Serializable
    @XmlSerialName("application", "", "")
    data class Application(
        @XmlSerialName("label", ANDROID_NS, "android")
        val label: String? = null,
        @XmlSerialName("icon", ANDROID_NS, "android")
        val icon: String? = null,
        @XmlSerialName("allowBackup", ANDROID_NS, "android")
        val allowBackup: Boolean? = null,
        @XmlSerialName("extractNativeLibs", ANDROID_NS, "android")
        val extractNativeLibs: Boolean? = null,
        @XmlElement(true)
        @XmlSerialName("meta-data", "", "")
        val metaData: List<MetaData> = emptyList(),
        @XmlElement(true)
        @XmlSerialName("activity", "", "")
        val activities: List<Activity> = emptyList(),
    )

    @Serializable
    @XmlSerialName("meta-data", "", "")
    data class MetaData(
        @XmlSerialName("name", ANDROID_NS, "android")
        val name: String,
        @XmlSerialName("value", ANDROID_NS, "android")
        val value: String? = null,
        @XmlSerialName("resource", ANDROID_NS, "android")
        val resource: String? = null,
    )

    @Serializable
    @XmlSerialName("activity", "", "")
    data class Activity(
        @XmlSerialName("name", ANDROID_NS, "android")
        val name: String,
        @XmlSerialName("exported", ANDROID_NS, "android")
        val exported: Boolean? = null,
        @XmlSerialName("theme", ANDROID_NS, "android")
        val theme: String? = null,
        @XmlElement(true)
        @XmlSerialName("intent-filter", "", "")
        val intentFilters: List<IntentFilter> = emptyList(),
    )

    @Serializable
    @XmlSerialName("intent-filter", "", "")
    data class IntentFilter(
        @XmlElement(true)
        @XmlSerialName("action", "", "")
        val actions: List<Action> = emptyList(),
        @XmlElement(true)
        @XmlSerialName("category", "", "")
        val categories: List<Category> = emptyList(),
        @XmlElement(true)
        @XmlSerialName("data", "", "")
        val data: List<Data> = emptyList(),
    )

    @Serializable
    @XmlSerialName("action", "", "")
    data class Action(
        @XmlSerialName("name", ANDROID_NS, "android")
        val name: String,
    )

    @Serializable
    @XmlSerialName("category", "", "")
    data class Category(
        @XmlSerialName("name", ANDROID_NS, "android")
        val name: String,
    )

    @Serializable
    @XmlSerialName("data", "", "")
    data class Data(
        @XmlSerialName("scheme", ANDROID_NS, "android")
        val scheme: String? = null,
        @XmlSerialName("host", ANDROID_NS, "android")
        val host: String? = null,
        @XmlSerialName("pathPattern", ANDROID_NS, "android")
        val pathPattern: String? = null,
    )

    @OptIn(ExperimentalXmlUtilApi::class)
    private val xml =
        XML {
            autoPolymorphic = false
            repairNamespaces = true
            xmlDeclMode = XmlDeclMode.Minimal
            defaultPolicy {
                unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
            }
        }

    @OptIn(ExperimentalXmlUtilApi::class)
    fun parse(input: InputStream): AndroidManifest = xml.decodeFromReader(AndroidManifest.serializer(), KtXmlReader(input))
}
