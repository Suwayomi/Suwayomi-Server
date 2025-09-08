package suwayomi.tachidesk.global.impl.util

import android.app.Application
import android.content.Context
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.Permissions
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
        val byteString = preferenceStore.getString(PREF_KEY, "")
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

    fun generateJwt(userId: Int): JwtTokens {
        val accessToken = createAccessToken(userId)
        val refreshToken = createRefreshToken(userId)

        return JwtTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    fun refreshJwt(refreshToken: String): String {
        val jwt = verifier.verify(refreshToken)
        require(jwt.getClaim("token_type").asString() == "refresh") {
            "Cannot use access token to refresh"
        }
        require(jwt.audience.single() == AUDIENCE) {
            "Token intended for different audience ${jwt.audience}"
        }
        return createAccessToken(jwt.subject.toInt())
    }

    fun verifyJwt(jwt: String): UserType {
        try {
            val decodedJWT = verifier.verify(jwt)

            require(decodedJWT.getClaim("token_type").asString() == "access") {
                "Cannot use refresh token to access"
            }
            require(decodedJWT.audience.single() == AUDIENCE) {
                "Token intended for different audience ${decodedJWT.audience}"
            }

            val user = decodedJWT.subject.toInt()
            val roles: List<String> = decodedJWT.getClaim("roles").asList(String::class.java)
            val permissions: List<String> = decodedJWT.getClaim("permissions").asList(String::class.java)

            return if (roles.any { it.equals("admin", ignoreCase = true) }) {
                UserType.Admin(user)
            } else {
                UserType.User(
                    id = user,
                    permissions =
                        permissions.mapNotNull { permission ->
                            Permissions.entries.find { it.name == permission }
                        },
                )
            }
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "Received invalid token" }
            return UserType.Visitor
        }
    }

    private fun createAccessToken(userId: Int): String {
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

        return jwt.sign(algorithm)
    }

    private fun createRefreshToken(userId: Int): String =
        JWT
            .create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withClaim("token_type", "refresh")
            .withExpiresAt(Instant.now().plusSeconds(refreshTokenExpiry.inWholeSeconds))
            .sign(algorithm)
}
