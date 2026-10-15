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
    // ktlint intentionally not applied yet: this module has pre-existing
    // violations and gets a dedicated formatting batch (see root
    // build.gradle.kts "Exempt:" note).
}

dependencies {
    // Core Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Coroutines for MutableStateFlow
    implementation(libs.coroutines.core)

    // Config handling
    implementation(libs.config)
    implementation(libs.config4k)

    // Logging
    implementation(libs.slf4japi)
    implementation(libs.kotlinlogging)

    // Database (for SortOrder enum used in ServerConfig)
    implementation(libs.exposed.core)

    // GraphQL types used in ServerConfig
    implementation(libs.graphql.kotlin.scheme)

    // Dependency Injection
    implementation(libs.injekt)

    // AndroidCompat for SystemPropertyOverridableConfigModule
    implementation(projects.androidCompat.config)

    // Serialization
    implementation(libs.serialization.json)
    implementation(libs.serialization.protobuf)
    implementation(project(":AndroidCompat"))
}
