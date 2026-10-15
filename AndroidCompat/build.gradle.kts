plugins {
    id(
        libs.plugins.kotlin.jvm
            .get()
            .pluginId,
    )
    id(
        libs.plugins.kotlin.serialization
            .get()
            .pluginId,
    )
    id(
        libs.plugins.ktlint
            .get()
            .pluginId,
    )
}

dependencies {
    // Shared
    implementation(libs.bundles.shared)
    testImplementation(libs.bundles.sharedTest)

    // Android stub library
    implementation(libs.android.stubs)

    // XML (runtime dependency of PersistableBundle.saveToXml/restoreFromXml)
    implementation(libs.xmlpull)

    // Config API
    implementation(projects.androidCompat.config)

    // APK sig verifier
    compileOnly(libs.apksig)

    // AndroidX annotations
    compileOnly(libs.android.annotations)

    // substitute for duktape-android/quickjs
    implementation(libs.bundles.polyglot)

    // Kotlin wrapper around Java Preferences, makes certain things easier
    implementation(libs.bundles.settings)

    // Android version of SimpleDateFormat
    implementation(libs.icu4j)

    // OpenJDK lacks native JPEG encoder and native WEBP decoder
    implementation(libs.bundles.twelvemonkeys)
    implementation(libs.imageio.webp)

    // The JDBC SQLite driver is looked up reflectively (Class.forName) at
    // runtime; downstream consumers supply it. Tests need it on the classpath.
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
}

tasks.test {
    useJUnitPlatform()
}
