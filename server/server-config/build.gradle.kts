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

}

dependencies {
    // Core Kotlin
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    
    // Coroutines for MutableStateFlow
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)
    
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
    
    // AndroidCompat for SystemPropertyOverridableConfigModule
    implementation(projects.androidCompat.config)
    
    // Serialization
    implementation(libs.serialization.json)
    implementation(libs.serialization.protobuf)
}

