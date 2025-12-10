package ireader.core.http

import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion as OkHttpTlsVersion
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Desktop implementation of SSL/TLS configuration
 */
 class SSLConfiguration {
    private var certificatePins: Map<String, List<String>> = emptyMap()
    private var allowSelfSigned: Boolean = false
    private var minTlsVersion: OkHttpTlsVersion = OkHttpTlsVersion.TLS_1_2
    
    fun applyTo(builder: OkHttpClient.Builder) {
        if (certificatePins.isNotEmpty()) {
            val pinner = CertificatePinner.Builder().apply {
                certificatePins.forEach { (domain, pins) ->
                    pins.forEach { pin ->
                        add(domain, "sha256/$pin")
                    }
                }
            }.build()
            builder.certificatePinner(pinner)
        }
        
        if (allowSelfSigned) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        
        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(
                when (minTlsVersion) {
                    OkHttpTlsVersion.TLS_1_0 -> OkHttpTlsVersion.TLS_1_0
                    OkHttpTlsVersion.TLS_1_1 -> OkHttpTlsVersion.TLS_1_1
                    OkHttpTlsVersion.TLS_1_2 -> OkHttpTlsVersion.TLS_1_2
                    OkHttpTlsVersion.TLS_1_3 -> OkHttpTlsVersion.TLS_1_3
                    else -> OkHttpTlsVersion.TLS_1_3
                }
            )
            .build()
        
        builder.connectionSpecs(listOf(connectionSpec, ConnectionSpec.CLEARTEXT))
    }
    
     fun enableCertificatePinning(pins: Map<String, List<String>>) {
        this.certificatePins = pins
    }
    
     fun allowSelfSignedCertificates() {
        this.allowSelfSigned = true
    }
    
     fun setMinimumTlsVersion(version: OkHttpTlsVersion) {
        this.minTlsVersion = version
    }
}
