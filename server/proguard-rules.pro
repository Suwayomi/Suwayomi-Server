-dontobfuscate
-keepattributes Signature,LineNumberTable

-keep,allowoptimization class eu.kanade.tachiyomi.** { public protected *; }
-keep,allowoptimization class suwayomi.tachidesk.** { public protected *; }
-keep class suwayomi.tachidesk.manga.model.dataclass.* { *; }
-keep class suwayomi.tachidesk.MainKt {
    public static void main(java.lang.String[]);
}
-keepdirectories suwayomi/tachidesk/**
-keepdirectories META-INF/**


# java.lang.ClassCastException: class javax.servlet.SessionTrackingMode not an enum
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Jackston
#-keep class kotlin.Metadata { *; }
#-keep class kotlin.reflect.** { *; }


# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.tachiyomi.**$$serializer { *; }
-keepclassmembers class eu.kanade.tachiyomi.** {
    *** Companion;
}
-keepclasseswithmembers class eu.kanade.tachiyomi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class suwayomi.tachidesk.**$$serializer { *; }
-keepclassmembers class suwayomi.tachidesk.** {
    *** Companion;
}
-keepclasseswithmembers class suwayomi.tachidesk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep extension's common dependencies
-keep,allowoptimization class eu.kanade.tachiyomi.** { public protected *; }
-keep,allowoptimization class androidx.preference.** { *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class com.google.gson.** { public protected *; }
-keep,allowoptimization class com.github.salomonbrys.kotson.** { public protected *; }
-keep,allowoptimization class com.squareup.duktape.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

# OKHTTP
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**

# Exposted
-keep class org.jetbrains.exposed.** { *; }
-keep class de.neonew.exposed.migrations.** { *; }

# H2
-keep class org.h2.Driver { *; }

# Javalin
-keep class org.eclipse.jetty.** { *; }
-dontwarn io.javalin.http.LeveledBrotliStream
-dontwarn io.javalin.plugin.metrics.**
-dontwarn io.javalin.plugin.rendering.**
-dontwarn com.nixxcode.jvmbrotli.common.BrotliLoader
-dontwarn com.nixxcode.jvmbrotli.enc.BrotliOutputStream

# Xml
-keep class org.apache.xerces.** { *; }
-dontwarn javax.xml.**
-dontwarn com.sun.xml.**
-dontwarn com.sun.org.apache.**
-dontwarn jdk.xml.internal.ErrorHandlerProxy
-dontwarn com.sun.beans.decoder.DocumentHandler
-dontwarn org.apache.xerces.**

# org.json
-dontwarn org.json.XMLTokener
-dontwarn org.json.JSONWriter

# Android
-dontwarn com.android.**
-dontwarn android.**
-dontwarn androidx.annotation.*

# Logback
-keep class ch.qos.logback.** { *; }
-dontwarn ch.qos.logback.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.slf4j.MDC
-dontwarn org.slf4j.MarkerFactory

# Dorkbox
-keep class dorkbox.systemTray.ui.** {
    public <init>(...);
}
-keep class dorkbox.jna.** { *; }
-dontwarn dorkbox.**

# Java
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.util.prefs.**

# Joda time
-dontwarn org.joda.convert.*

# Other
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.rowset.**
-dontwarn com.reprezen.jsonoverlay.gen.**
-dontwarn org.h2.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.antlr.runtime.tree.DOTTreeGenerator
