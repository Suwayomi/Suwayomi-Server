
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
    implementation(fileTree("lib/"))


    // Android JAR libs
//    compileOnly( fileTree(dir: new File(rootProject.rootDir, "libs/other"), include: "*.jar")

    // JSON
    compileOnly( "com.google.code.gson:gson:2.8.6")

    // Javassist
    compileOnly( "org.javassist:javassist:3.27.0-GA")

    // XML
    compileOnly( group= "xmlpull", name= "xmlpull", version= "1.1.3.1")

    // Config API
    implementation(project(":AndroidCompat:Config"))

    // dex2jar: https://github.com/DexPatcher/dex2jar/releases/tag/v2.1-20190905-lanchon
    compileOnly("com.github.DexPatcher.dex2jar:dex-tools:v2.1-20190905-lanchon")

    // APK parser
    compileOnly("net.dongliu:apk-parser:2.6.10")

    // APK sig verifier
    compileOnly("com.android.tools.build:apksig:4.2.0-alpha13")

    // AndroidX annotations
    compileOnly( "androidx.annotation:annotation:1.2.0-alpha01")

    // substitute for duktape-android
    // 'org.mozilla:rhino' includes some code that we don't need so use 'org.mozilla:rhino-runtime' instead
    implementation("org.mozilla:rhino-runtime:1.7.13")
    // 'org.mozilla:rhino-engine' provides the same interface as 'javax.script' a.k.a Nashorn
    implementation("org.mozilla:rhino-engine:1.7.13")
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
