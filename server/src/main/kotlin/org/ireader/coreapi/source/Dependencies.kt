package org.ireader.coreapi.source

import org.ireader.coreapi.http.HttpClientsInterface
import org.ireader.coreapi.prefs.PreferenceStore

/**
 * Dependencies required by IReader sources
 */
class Dependencies(
    val httpClients: HttpClientsInterface,
    val preferences: PreferenceStore,
)
