package suwayomi.tachidesk.opds.util

import io.javalin.http.Context
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.UnauthorizedException
import suwayomi.tachidesk.server.user.UserType

/**
 * OPDS-local variant of
 * [suwayomi.tachidesk.server.user.requireUserWithBasicFallback] that resolves the
 * *Basic-auth username* to the matching `USERACCOUNT` row instead of unconditionally
 * acting as the built-in admin (user id `1`).
 *
 * Rationale: `getUserFromContext` maps **every** request in [AuthMode.BASIC_AUTH] to
 * `Admin(1)` — a pseudo-identity meaning "whoever passed the global Basic check", not a
 * real admin session. When several `USERACCOUNT`s exist, a caller whose Basic username
 * matches a non-admin account must see *their own* library, reading progress and
 * history — not user `1`'s.
 *
 * Fallback: when no `USERACCOUNT` row matches the Basic username (typical for legacy
 * single-user installs where `authUsername` is not a registered account), the built-in
 * admin user `1` is kept — same as before — so single-user Basic deployments keep
 * working.
 */
fun UserType.requireUserWithResolvedBasicFallback(ctx: Context): Int =
    when (this) {
        // In BASIC_AUTH mode every authenticated request arrives as `Admin(1)` (see
        // `getUserFromContext`); the Basic credentials were already enforced by
        // `JavalinSetup.beforeMatched`, so resolve the presented username here.
        is UserType.Admin if serverConfig.authMode.value == AuthMode.BASIC_AUTH -> {
            resolveBasicUserId(ctx)
        }

        is UserType.Admin -> {
            id
        }

        // Defensive: Basic credentials only ever reach this branch after
        // `JavalinSetup.beforeMatched` validated them (Attribute.TachideskBasic) in
        // BASIC_AUTH mode; a Basic header no longer upgrades a Visitor in
        // UI_LOGIN/SIMPLE_LOGIN.
        UserType.Visitor if
        ctx.getAttribute(Attribute.TachideskBasic) &&
            serverConfig.authMode.value == AuthMode.BASIC_AUTH
        -> {
            resolveBasicUserId(ctx)
        }

        UserType.Visitor -> {
            ctx.header("WWW-Authenticate", "Basic")
            throw UnauthorizedException()
        }

        is UserType.User -> {
            id
        }
    }

/**
 * Resolves the validated Basic-auth username to its `USERACCOUNT` id.
 * `TachideskBasic == true` means the presented credentials matched the configured
 * `serverConfig.authUsername`, so the request's Basic username (or, defensively, the
 * configured one) is the account name to look up.
 */
private fun resolveBasicUserId(ctx: Context): Int {
    val username = ctx.basicAuthCredentials()?.username ?: serverConfig.authUsername.value
    return resolveBasicUsernameToId(username)
}

/**
 * Maps a validated Basic-auth username to its `USERACCOUNT` id.
 *
 * Single-user backward compatibility: usernames that exist nowhere in USERACCOUNT keep
 * the historical built-in-admin view (id 1).
 */
internal fun resolveBasicUsernameToId(username: String): Int =
    transaction {
        UserAccountTable
            .select(UserAccountTable.id)
            // case-insensitive, matching the rest of the codebase (registration, login,
            // username-taken checks all compare lowerCase()): a case mismatch must not
            // silently fall back to the built-in admin view
            .where { UserAccountTable.username.lowerCase() eq username.lowercase() }
            .firstOrNull()
            ?.get(UserAccountTable.id)
            ?.value
    } ?: 1
