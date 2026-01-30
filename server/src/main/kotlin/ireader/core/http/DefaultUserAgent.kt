package ireader.core.http

import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.injectLazy

/**
 * Default user agent provider.
 * Uses NetworkHelper.userAgent which can be updated by FlareSolverr.
 */
private val networkHelper: NetworkHelper by injectLazy()

/**
 * Get the current default user agent.
 * This value can be updated dynamically by FlareSolverr.
 */
val DEFAULT_USER_AGENT: String
    get() = networkHelper.defaultUserAgentProvider()
