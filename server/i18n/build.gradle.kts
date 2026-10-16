import com.google.gson.Gson // provided via buildSrc (see buildSrc/build.gradle.kts)
import com.google.gson.JsonArray
import com.google.gson.JsonObject

plugins {
    id(
        libs.plugins.kotlin.multiplatform
            .get()
            .pluginId,
    )
    id(
        libs.plugins.moko
            .get()
            .pluginId,
    )
    id(
        libs.plugins.ktlint
            .get()
            .pluginId,
    )
}

kotlin {
    jvm()

    sourceSets {
        getByName("jvmMain") {
            dependencies {
                api(libs.moko)
            }
        }
    }
}

multiplatformResources {
    resourcesPackage = "suwayomi.tachidesk.i18n"
}

tasks {
    register("generateLocales") {
        group = "moko-resources"
        doFirst {
            val langs =
                listOf("en") +
                    file("src/commonMain/moko-resources/values")
                        .listFiles()
                        ?.map { it.name }
                        ?.minus("base")
                        ?.map { it.replace("-r", "-") }
                        ?.sorted()
                        .orEmpty()

            val langFile = file("src/commonMain/moko-resources/files/languages.json", PathValidation.NONE)
            if (langFile.exists()) {
                val currentLangs =
                    langFile.reader().use {
                        Gson()
                            .fromJson(it, JsonObject::class.java)
                            .getAsJsonArray("langs")
                            .mapNotNull { it.asString }
                            .toSet()
                    }

                if (currentLangs == langs.toSet()) return@doFirst
            }
            langFile.parentFile.mkdirs()

            val json =
                JsonObject().apply {
                    val array =
                        JsonArray().apply {
                            langs.forEach(::add)
                        }
                    add("langs", array)
                }

            langFile.writer().use {
                Gson().toJson(json, it)
            }
        }
    }

    // languages.json is consumed by the moko resource generators; regenerate
    // it as part of the build instead of relying on manual task invocation.
    val generateLocales = named("generateLocales")
    matching { it.name.startsWith("generateMR") }.configureEach {
        dependsOn(generateLocales)
    }
}
