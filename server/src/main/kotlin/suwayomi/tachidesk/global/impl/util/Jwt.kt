package suwayomi.tachidesk.global.impl.util

import android.app.Application
import android.content.Context
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.SessionVersion
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.UserType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object Jwt {
    private val preferenceStore =
        Injekt.get<Application>().getSharedPreferences("jwt", Context.MODE_PRIVATE)
    private val logger = KotlinLogging.logger {}

    private const val ALGORITHM = "HmacSHA256"
    private val accessTokenExpiry get() = serverConfig.jwtTokenExpiry.value
    private val refreshTokenExpiry get() = serverConfig.jwtRefreshExpiry.value
    private const val ISSUER = "suwayomi-server"
    private val AUDIENCE get() = serverConfig.jwtAudience.value

    private const val PREF_KEY = "jwt_key"

    @OptIn(ExperimentalEncodingApi::class)
    fun generateSecret(): String {
        val byteString = preferenceStore.getString(PREF_KEY, "").orEmpty()
        val decodedKeyBytes =
            try {
                Base64.Default.decode(byteString)
            } catch (e: IllegalArgumentException) {
                logger.warn(e) { "Invalid key specified, regenerating" }
                null
            }

        val keyBytes =
            if (decodedKeyBytes?.size == 32) {
                decodedKeyBytes
            } else {
                val k = ByteArray(32)
                SecureRandom().nextBytes(k)
                preferenceStore.edit().putString(PREF_KEY, Base64.Default.encode(k)).apply()
                k
            }

        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        return Base64.encode(secretKey.encoded)
    }

    private val algorithm: Algorithm = Algorithm.HMAC256(generateSecret())
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    class JwtTokens(
        val accessToken: String,
        val refreshToken: String,
    )

    suspend fun generateJwt(userId: Int): JwtTokens {
        val accessToken = createAccessToken(userId)
        val refreshToken = createRefreshToken(userId)

        return JwtTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    suspend fun refreshJwt(refreshToken: String): String {
        val jwt = verifier.verify(refreshToken)
        require(jwt.getClaim("token_type").asString() == "refresh") {
            "Cannot use access token to refresh"
        }
        require(jwt.audience.single() == AUDIENCE) {
            "Token intended for different audience ${jwt.audience}"
        }

        // without this check, a stale refresh token would keep minting valid access tokens
        // after the account's session version was bumped
        val user = jwt.subject.toInt()
        val tokenVersion = jwt.getClaim("token_version").asInt()
        require(tokenVersion == SessionVersion.current(user)) {
            "Token revoked by a password change"
        }

        return createAccessToken(user)
    }

    suspend fun verifyJwt(jwt: String): UserType {
        try {
            val decodedJWT = verifier.verify(jwt)

            require(decodedJWT.getClaim("token_type").asString() == "access") {
                "Cannot use refresh token to access"
            }
            require(decodedJWT.audience.single() == AUDIENCE) {
                "Token intended for different audience ${decodedJWT.audience}"
            }

            val user = decodedJWT.subject.toInt()

            // tokens minted before a session version bump (e.g. a password change) are
            // rejected; tokens lacking the claim entirely are rejected as well
            val tokenVersion = decodedJWT.getClaim("token_version").asInt()
            if (tokenVersion != SessionVersion.current(user)) {
                logger.warn { "Token version mismatch for user $user" }
                return UserType.Visitor
            }

            val roles: List<UserRole> =
                decodedJWT
                    .getClaim("roles")
                    .asList(String::class.java)
                    .mapNotNull { role ->
                        UserRole.entries.find { it.name == role }
                    }
            val permissions: List<UserPermission> =
                decodedJWT
                    .getClaim("permissions")
                    .asList(String::class.java)
                    .mapNotNull { permission ->
                        UserPermission.entries.find { it.name == permission }
                    }

            return if (roles.any { it == UserRole.ADMIN }) {
                UserType.Admin(user, roles)
            } else {
                UserType.User(
                    id = user,
                    permissions = permissions,
                    roles = roles,
                )
            }
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "Received invalid token" }
            return UserType.Visitor
        } catch (e: Exception) {
            // verifyJwt runs on every request; fail closed (logged out) rather than
            // surfacing a 500 for unexpected verification failures
            logger.warn(e) { "Failed to verify token" }
            return UserType.Visitor
        }
    }

    private suspend fun createAccessToken(userId: Int): String {
        val jwt =
            JWT
                .create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject(userId.toString())
                .withClaim("token_type", "access")
                .withExpiresAt(Instant.now().plusSeconds(accessTokenExpiry.inWholeSeconds))

        val roles =
            transaction {
                UserRolesTable
                    .selectAll()
                    .where { UserRolesTable.user eq userId }
                    .toList()
                    .map { it[UserRolesTable.role] }
            }
        val permissions =
            transaction {
                UserPermissionsTable
                    .selectAll()
                    .where { UserPermissionsTable.user eq userId }
                    .toList()
                    .map { it[UserPermissionsTable.permission] }
            }

        jwt.withClaim("roles", roles)

        jwt.withClaim("permissions", permissions)

        jwt.withClaim("token_version", SessionVersion.current(userId))

        return jwt.sign(algorithm)
    }

    private suspend fun createRefreshToken(userId: Int): String =
        JWT
            .create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withClaim("token_type", "refresh")
            .withClaim("token_version", SessionVersion.current(userId))
            .withExpiresAt(Instant.now().plusSeconds(refreshTokenExpiry.inWholeSeconds))
            .sign(algorithm)
}
