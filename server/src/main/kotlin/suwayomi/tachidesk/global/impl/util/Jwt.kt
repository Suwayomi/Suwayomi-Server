package suwayomi.tachidesk.global.impl.util

import android.app.Application
import android.content.Context
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.serverConfig
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

    private val algorithm: Algorithm = Algorithm.HMAC256(generateSecret()) // todo store secret
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    class JwtTokens(
        val accessToken: String,
        val refreshToken: String,
    )

    fun generateJwt(): JwtTokens {
        val accessToken = createAccessToken()
        val refreshToken = createRefreshToken()

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
        return createAccessToken()
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

            return UserType.Admin(1)
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "Received invalid token" }
            return UserType.Visitor
        }
    }

    private fun createAccessToken(): String {
        val jwt =
            JWT
                .create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withClaim("token_type", "access")
                .withExpiresAt(Instant.now().plusSeconds(accessTokenExpiry.inWholeSeconds))

        return jwt.sign(algorithm)
    }

    private fun createRefreshToken(): String =
        JWT
            .create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("token_type", "refresh")
            .withExpiresAt(Instant.now().plusSeconds(refreshTokenExpiry.inWholeSeconds))
            .sign(algorithm)
}
