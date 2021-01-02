plugins {
//    id("org.jetbrains.kotlin.jvm") version "1.4.21"
    application
}


repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
//    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Source models and interfaces from Tachiyomi 1.x
    // using source class from tachiyomi commit 9493577de27c40ce8b2b6122cc447d025e34c477 to not depend on tachiyomi.sourceapi
//    implementation("tachiyomi.sourceapi:source-api:1.1")

    implementation("com.github.inorichi.injekt:injekt-core:65b0440")

    val okhttp_version = "4.10.0-RC1"
    implementation("com.squareup.okhttp3:okhttp:$okhttp_version")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttp_version")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:$okhttp_version")
    implementation("com.squareup.okio:okio:2.9.0")


    // retrofit
    val retrofit_version = "2.9.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofit_version")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:0.8.0")
    implementation("com.squareup.retrofit2:converter-gson:$retrofit_version")
    implementation("com.squareup.retrofit2:adapter-rxjava:$retrofit_version")


    // reactivex
    implementation("io.reactivex:rxjava:1.3.8")
//    implementation("io.reactivex:rxandroid:1.2.1")
//    implementation("com.jakewharton.rxrelay:rxrelay:1.2.0")
//    implementation("com.github.pwittchen:reactivenetwork:0.13.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    implementation("org.jsoup:jsoup:1.13.1")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")
    implementation("com.squareup.duktape:duktape-android:1.3.0")


    val coroutinesVersion = "1.3.9"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // dex2jar
    implementation(fileTree("lib/dex2jar/"))

    // api
    implementation("io.javalin:javalin:3.12.0")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4")
    implementation("org.slf4j:slf4j-api:1.8.0-beta4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.3")

    // to get application content root
    implementation("net.harawata:appdirs:1.2.0")

    // Exposed ORM
    val exposed_version = "0.28.1"
    implementation ("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation ("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation ("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation ("org.xerial:sqlite-jdbc:3.30.1")

    // AndroidCompat
    implementation(project(":AndroidCompat"))
    implementation(project(":AndroidCompat:Config"))


    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    mainClass.set("ir.armor.tachidesk.Main")
}
