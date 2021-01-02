
plugins {
    application
}


repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("https://maven.google.com")
    }
}

dependencies {
    // Android stub library
//    compileOnly( fileTree(File(rootProject.rootDir, "libs/android"), include: "*.jar")
    implementation(fileTree("lib/"))
    implementation(fileTree("${rootProject.rootDir}/server/lib/dex2jar/"))


    // Android JAR libs
//    compileOnly( fileTree(dir: new File(rootProject.rootDir, "libs/other"), include: "*.jar")

    // JSON
    compileOnly( "com.google.code.gson:gson:2.8.6")

    // Javassist
    compileOnly( "org.javassist:javassist:3.27.0-GA")

    // Coroutines
    val kotlinx_coroutines_version = "1.4.2"
    compileOnly( "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
    compileOnly( "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinx_coroutines_version")

    // XML
    compileOnly( group= "xmlpull", name= "xmlpull", version= "1.1.3.1")

    // Config API
    implementation( project(":AndroidCompat:Config"))

    // dex2jar
//    compileOnly( "dex2jar:dex-translator")

    // APK parser
    compileOnly("net.dongliu:apk-parser:2.6.10")

    // APK sig verifier
    compileOnly("com.android.tools.build:apksig:4.2.0-alpha13")

    // AndroidX annotations
    compileOnly( "androidx.annotation:annotation:1.2.0-alpha01")

//    compileOnly("io.reactivex:rxjava:1.3.8")
}

//def fatJarTask = tasks.getByPath(':AndroidCompat:JVMPatch:fatJar')
//
//// Copy JVM core patches
//task copyJVMPatches(type: Copy) {
//    from fatJarTask.outputs.files
//    into 'src/main/resources/patches'
//}
//
//compileOnly(Java.dependsOn gradle.includedBuild('dex2jar').task(':dex-translator:assemble')
//compileOnly(Java.dependsOn copyJVMPatches
//copyJVMPatches.dependsOn fatJarTask
//
