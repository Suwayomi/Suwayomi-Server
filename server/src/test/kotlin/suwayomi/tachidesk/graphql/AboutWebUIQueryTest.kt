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
        val originalEnabled = serverConfig.webUIEnabled.value
        val revisionFile = File(Injekt.get<ApplicationDirs>().webUIRoot, "revision")
        val originalRevision = revisionFile.takeIf { it.isFile }?.readBytes()
        check(!revisionFile.exists() || revisionFile.delete())

        try {
            serverConfig.webUIEnabled.value = false

            val response =
                graphql(
                    """
                    query {
                        aboutServer {
                            name
                        }
                        aboutWebUI {
                            tag
                        }
                    }
                    """.trimIndent(),
                )

            response.assertNoErrors()
            assertNotNull(response.dataPath("aboutServer", "name"))
            assertEquals("r-1", response.dataPath("aboutWebUI", "tag"))
        } finally {
            serverConfig.webUIEnabled.value = originalEnabled
            originalRevision?.let { revisionFile.writeBytes(it) }
        }
    }
}
