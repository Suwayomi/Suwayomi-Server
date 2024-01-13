plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.ktlint.get().pluginId)
}

dependencies {
    // Shared
    implementation(libs.bundles.shared)
    testImplementation(libs.bundles.sharedTest)

    // Android stub library
    implementation(libs.android.stubs)

    // XML
    compileOnly(libs.xmlpull)

    // Config API
    implementation(projects.androidCompat.config)

    // APK sig verifier
    compileOnly(libs.apksig)

    // AndroidX annotations
    compileOnly(libs.android.annotations)

    // substitute for duktape-android
    implementation(libs.bundles.rhino)

    // Kotlin wrapper around Java Preferences, makes certain things easier
    implementation(libs.bundles.settings)

    // Android version of SimpleDateFormat
    implementation(libs.icu4j)

    // OpenJDK lacks native JPEG encoder and native WEBP decoder
    implementation(libs.bundles.twelvemonkeys)
}
