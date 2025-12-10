

package ireader.core.source

import ireader.core.http.HttpClientsInterface
import ireader.core.prefs.PreferenceStore

class Dependencies(
    val httpClients: HttpClientsInterface,
    val preferences: PreferenceStore
)
