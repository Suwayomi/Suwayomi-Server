package masstest

import android.os.Looper
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.context.stopKoin
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.server.applicationSetup
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.test.BASE_PATH
import suwayomi.tachidesk.test.setLoggingEnabled
import xyz.nulldev.ts.config.CONFIG_PREFIX
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudFlareTest {
    lateinit var nhentai: HttpSource

    @BeforeAll
    fun setup() {
        val dataRoot = File(BASE_PATH).absolutePath
        System.setProperty("$CONFIG_PREFIX.server.rootDir", dataRoot)
        Looper.clearMainLooperForTest()
        SettingsRegistry.clear()
        applicationSetup()
        setLoggingEnabled(false)
        return

        runBlocking {
            val extensions = ExtensionsList.getExtensionList()
            with(extensions.first { it.name == "NHentai" }) {
                if (!installed) {
                    Extension.installExtension(pkgName)
                } else if (hasUpdate) {
                    Extension.updateExtension(pkgName)
                }
                Unit
            }

            nhentai =
                Source
                    .getSourceList()
                    .firstNotNullOf { it.id.toLong().takeIf { it == 3122156392225024195L } }
                    .let { GetSource.getSourceOrNull(it) } as HttpSource
        }
        setLoggingEnabled(true)
    }

    @AfterAll
    fun teardown() {
        stopKoin()
    }

    private val logger = KotlinLogging.logger {}

    @Test
    @Disabled
    fun `test nhentai browse`() =
        runTest {
            assert(nhentai.getPopularManga(1).mangas.isNotEmpty()) {
                "NHentai results were empty"
            }
        }
}
