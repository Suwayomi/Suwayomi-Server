package suwayomi.tachidesk.graphql

import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.GraphQLTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AboutWebUIQueryTest : GraphQLTest() {
    @Test
    fun disabledWebUIWithoutLocalVersionDoesNotFailServerInfoQuery() {
        withWebUIVersion(enabled = false, revision = null) {
            val response = graphql(SERVER_INFO_QUERY)

            response.assertNoErrors()
            assertNotNull(response.dataPath("aboutServer", "name"))
            assertEquals("r-1", response.dataPath("aboutWebUI", "tag"))
        }
    }

    @Test
    fun enabledWebUIWithoutLocalVersionStillReportsAnError() {
        withWebUIVersion(enabled = true, revision = null) {
            graphql(SERVER_INFO_QUERY).assertHasError()
        }
    }

    @Test
    fun localVersionIsPreservedWhetherWebUIIsEnabledOrDisabled() {
        for (enabled in listOf(false, true)) {
            withWebUIVersion(enabled = enabled, revision = "r1234\n") {
                val response = graphql(SERVER_INFO_QUERY)

                response.assertNoErrors()
                assertNotNull(response.dataPath("aboutServer", "name"))
                assertEquals("r1234", response.dataPath("aboutWebUI", "tag"))
            }
        }
    }

    private fun withWebUIVersion(
        enabled: Boolean,
        revision: String?,
        block: () -> Unit,
    ) {
        val originalEnabled = serverConfig.webUIEnabled.value
        val revisionFile = File(Injekt.get<ApplicationDirs>().webUIRoot, "revision")
        val originalRevision = revisionFile.takeIf { it.isFile }?.readBytes()

        try {
            if (revision == null) {
                check(!revisionFile.exists() || revisionFile.delete())
            } else {
                revisionFile.parentFile.mkdirs()
                revisionFile.writeText(revision)
            }

            serverConfig.webUIEnabled.value = enabled
            block()
        } finally {
            serverConfig.webUIEnabled.value = originalEnabled
            if (originalRevision != null) {
                revisionFile.writeBytes(originalRevision)
            } else {
                check(!revisionFile.exists() || revisionFile.delete())
            }
        }
    }

    companion object {
        private val SERVER_INFO_QUERY =
            """
            query {
                aboutServer {
                    name
                }
                aboutWebUI {
                    tag
                }
            }
            """.trimIndent()
    }
}
