package xyz.nulldev.androidcompat.webkit

import android.webkit.WebSettings
import kotlinx.serialization.Serializable
import kotlin.collections.Map

class KcefWebSettings : WebSettings() {
    // Boolean settings
    private var navDumps = false
    private var mediaPlaybackRequiresUserGesture = true
    private var builtInZoomControls = false
    private var displayZoomControls = true
    private var allowFileAccess = true
    private var allowContentAccess = true
    private var pluginsEnabled = false
    private var loadWithOverviewMode = false
    private var enableSmoothTransition = false
    private var useWebViewBackgroundForOverscrollBackground = false
    private var saveFormData = true
    private var savePassword = false
    private var acceptThirdPartyCookies = true
    private var lightTouchEnabled = false
    private var useWideViewPort = true
    private var supportMultipleWindows = false
    private var loadsImagesAutomatically = true
    private var blockNetworkImage = false
    private var blockNetworkLoads = false
    private var javaScriptEnabled = true
    private var allowUniversalAccessFromFileURLs = false
    private var allowFileAccessFromFileURLs = false
    private var domStorageEnabled = true
    private var geolocationEnabled = true
    private var javaScriptCanOpenWindowsAutomatically = false
    private var needInitialFocus = false
    private var offscreenPreRaster = false
    private var videoOverlayForEmbeddedEncryptedVideoEnabled = false
    private var safeBrowsingEnabled = true

    // Integer settings
    private var textZoom = 100
    private var minimumFontSize = 8
    private var minimumLogicalFontSize = 8
    private var defaultFontSize = 16
    private var defaultFixedFontSize = 13
    private var cacheMode = 0
    private var mixedContentMode = 0
    private var disabledActionModeMenuItems = 0

    // String settings
    private var databasePath: String? = null
    private var geolocationDatabasePath: String? = null
    private var appCachePath: String? = null
    private var defaultTextEncodingName: String? = null
    private var userAgentString: String? = null
    private var userAgentMetadata: UserAgentMetadata? = null
    private var standardFontFamily: String? = null
    private var fixedFontFamily: String? = null
    private var sansSerifFontFamily: String? = null
    private var serifFontFamily: String? = null
    private var cursiveFontFamily: String? = null
    private var fantasyFontFamily: String? = null

    // Enum settings
    private var defaultZoom: ZoomDensity? = null
    private var layoutAlgorithm: LayoutAlgorithm? = null
    private var pluginState: PluginState? = null
    private var renderPriority: RenderPriority? = null

    // Long settings
    private var appCacheMaxSize: Long = 0L

    // Implementations

    @SuppressWarnings("HiddenAbstractMethod")
    @Deprecated("inherit")
    override fun setNavDump(p0: Boolean) {
        navDumps = p0
    }

    @Deprecated("inherit")
    @SuppressWarnings("HiddenAbstractMethod")
    override fun getNavDump(): Boolean = navDumps

    override fun setSupportZoom(p0: Boolean) {
        mediaPlaybackRequiresUserGesture = p0
    }

    override fun supportZoom() = mediaPlaybackRequiresUserGesture

    override fun setMediaPlaybackRequiresUserGesture(p0: Boolean) {
        mediaPlaybackRequiresUserGesture = p0
    }

    override fun getMediaPlaybackRequiresUserGesture() = mediaPlaybackRequiresUserGesture

    override fun setBuiltInZoomControls(p0: Boolean) {
        builtInZoomControls = p0
    }

    override fun getBuiltInZoomControls() = builtInZoomControls

    override fun setDisplayZoomControls(p0: Boolean) {
        displayZoomControls = p0
    }

    override fun getDisplayZoomControls() = displayZoomControls

    override fun setAllowFileAccess(p0: Boolean) {
        allowFileAccess = p0
    }

    override fun getAllowFileAccess() = allowFileAccess

    override fun setAllowContentAccess(p0: Boolean) {
        allowContentAccess = p0
    }

    override fun getAllowContentAccess() = allowContentAccess

    override fun setLoadWithOverviewMode(p0: Boolean) {
        loadWithOverviewMode = p0
    }

    override fun getLoadWithOverviewMode() = loadWithOverviewMode

    @Deprecated("inherit")
    override fun setPluginsEnabled(p0: Boolean) {
        pluginsEnabled = p0
    }

    @Deprecated("inherit")
    override fun getPluginsEnabled() = pluginsEnabled

    @Deprecated("inherit")
    override fun setEnableSmoothTransition(p0: Boolean) {
        enableSmoothTransition = p0
    }

    @Deprecated("inherit")
    override fun enableSmoothTransition() = enableSmoothTransition

    @Deprecated("inherit")
    override fun setUseWebViewBackgroundForOverscrollBackground(p0: Boolean) {
        useWebViewBackgroundForOverscrollBackground = p0
    }

    @Deprecated("inherit")
    override fun getUseWebViewBackgroundForOverscrollBackground() = useWebViewBackgroundForOverscrollBackground

    @Deprecated("inherit")
    override fun setSaveFormData(p0: Boolean) {
        saveFormData = p0
    }

    @Deprecated("inherit")
    override fun getSaveFormData() = saveFormData

    @Deprecated("inherit")
    override fun setSavePassword(p0: Boolean) {
        savePassword = p0
    }

    @Deprecated("inherit")
    override fun getSavePassword() = savePassword

    override fun setTextZoom(p0: Int) {
        textZoom = p0
    }

    override fun getTextZoom() = textZoom

    @Deprecated("inherit")
    override fun setDefaultZoom(p0: ZoomDensity?) {
        defaultZoom = p0
    }

    override fun setAcceptThirdPartyCookies(p0: Boolean) {
        acceptThirdPartyCookies = p0
    }

    override fun getAcceptThirdPartyCookies() = acceptThirdPartyCookies

    @Deprecated("inherit")
    override fun getDefaultZoom() = defaultZoom

    @Deprecated("inherit")
    override fun setLightTouchEnabled(p0: Boolean) {
        lightTouchEnabled = p0
    }

    @Deprecated("inherit")
    override fun getLightTouchEnabled() = lightTouchEnabled

    @Deprecated("inherit")
    override fun setUserAgent(ua: Int) = throw RuntimeException("Stub!")

    @Deprecated("inherit")
    override fun getUserAgent(): Int = throw RuntimeException("Stub!")

    override fun setUseWideViewPort(p0: Boolean) {
        useWideViewPort = p0
    }

    override fun getUseWideViewPort() = useWideViewPort

    override fun setSupportMultipleWindows(p0: Boolean) {
        supportMultipleWindows = p0
    }

    override fun supportMultipleWindows() = supportMultipleWindows

    override fun setLayoutAlgorithm(p0: LayoutAlgorithm?) {
        layoutAlgorithm = p0
    }

    override fun getLayoutAlgorithm() = layoutAlgorithm

    override fun setStandardFontFamily(p0: String?) {
        standardFontFamily = p0
    }

    override fun getStandardFontFamily() = standardFontFamily

    override fun setFixedFontFamily(p0: String?) {
        fixedFontFamily = p0
    }

    override fun getFixedFontFamily() = fixedFontFamily

    override fun setSansSerifFontFamily(p0: String?) {
        sansSerifFontFamily = p0
    }

    override fun getSansSerifFontFamily() = sansSerifFontFamily

    override fun setSerifFontFamily(p0: String?) {
        serifFontFamily = p0
    }

    override fun getSerifFontFamily() = serifFontFamily

    override fun setCursiveFontFamily(p0: String?) {
        cursiveFontFamily = p0
    }

    override fun getCursiveFontFamily() = cursiveFontFamily

    override fun setFantasyFontFamily(p0: String?) {
        fantasyFontFamily = p0
    }

    override fun getFantasyFontFamily() = fantasyFontFamily

    override fun setMinimumFontSize(p0: Int) {
        minimumFontSize = p0
    }

    override fun getMinimumFontSize() = minimumFontSize

    override fun setMinimumLogicalFontSize(p0: Int) {
        minimumLogicalFontSize = p0
    }

    override fun getMinimumLogicalFontSize() = minimumLogicalFontSize

    override fun setDefaultFontSize(p0: Int) {
        defaultFontSize = p0
    }

    override fun getDefaultFontSize() = defaultFontSize

    override fun setDefaultFixedFontSize(p0: Int) {
        defaultFixedFontSize = p0
    }

    override fun getDefaultFixedFontSize() = defaultFixedFontSize

    override fun setLoadsImagesAutomatically(p0: Boolean) {
        loadsImagesAutomatically = p0
    }

    override fun getLoadsImagesAutomatically() = loadsImagesAutomatically

    override fun setBlockNetworkImage(p0: Boolean) {
        blockNetworkImage = p0
    }

    override fun getBlockNetworkImage() = blockNetworkImage

    override fun setBlockNetworkLoads(p0: Boolean) {
        blockNetworkLoads = p0
    }

    override fun getBlockNetworkLoads() = blockNetworkLoads

    override fun setJavaScriptEnabled(p0: Boolean) {
        javaScriptEnabled = p0
    }

    override fun getJavaScriptEnabled() = javaScriptEnabled

    @Deprecated("inherit")
    override fun setAllowUniversalAccessFromFileURLs(p0: Boolean) {
        allowUniversalAccessFromFileURLs = p0
    }

    override fun getAllowUniversalAccessFromFileURLs() = allowUniversalAccessFromFileURLs

    @Deprecated("inherit")
    override fun setAllowFileAccessFromFileURLs(p0: Boolean) {
        allowFileAccessFromFileURLs = p0
    }

    override fun getAllowFileAccessFromFileURLs() = allowFileAccessFromFileURLs

    @Deprecated("inherit")
    override fun setPluginState(p0: PluginState?) {
        pluginState = p0
    }

    @Deprecated("inherit")
    override fun getPluginState() = pluginState

    @Deprecated("inherit")
    override fun setDatabasePath(p0: String?) {
        databasePath = p0
    }

    @Deprecated("inherit")
    override fun getDatabasePath() = databasePath ?: ""

    @Deprecated("inherit")
    override fun setGeolocationDatabasePath(p0: String?) {
        geolocationDatabasePath = p0
    }

    @Deprecated("inherit")
    override fun setAppCacheEnabled(p0: Boolean) {}

    @Deprecated("inherit")
    override fun setAppCachePath(p0: String?) {
        appCachePath = p0
    }

    @Deprecated("inherit")
    override fun setAppCacheMaxSize(p0: Long) {
        appCacheMaxSize = p0
    }

    @Deprecated("inherit")
    override fun setDatabaseEnabled(p0: Boolean) {}

    @Deprecated("inherit")
    override fun getDatabaseEnabled() = true

    override fun setDomStorageEnabled(p0: Boolean) {
        domStorageEnabled = p0
    }

    override fun getDomStorageEnabled() = domStorageEnabled

    override fun setGeolocationEnabled(p0: Boolean) {
        geolocationEnabled = p0
    }

    override fun setJavaScriptCanOpenWindowsAutomatically(p0: Boolean) {
        javaScriptCanOpenWindowsAutomatically = p0
    }

    override fun getJavaScriptCanOpenWindowsAutomatically() = javaScriptCanOpenWindowsAutomatically

    override fun setDefaultTextEncodingName(p0: String?) {
        defaultTextEncodingName = p0
    }

    override fun getDefaultTextEncodingName() = defaultTextEncodingName ?: ""

    override fun setUserAgentString(p0: String?) {
        userAgentString = p0
        userAgentMetadata = null // reset metadata; if the client wants custom meta, they must provide it afterwards
    }

    override fun getUserAgentString() = userAgentString ?: defaultUserAgent()

    fun setUserAgentMetadataFromMap(uaMetadata: Map<String, Any>?) {
        userAgentMetadata =
            uaMetadata?.let {
                UserAgentMetadata.fromMap(it)
            }
    }

    fun getUserAgentMetadataMap() = (userAgentMetadata ?: defaultUserAgentMetadata()).toMap()

    fun getUserAgentMetadata() = userAgentMetadata

    override fun setNeedInitialFocus(p0: Boolean) {
        needInitialFocus = p0
    }

    @Deprecated("inherit")
    override fun setRenderPriority(p0: RenderPriority?) {
        renderPriority = p0
    }

    override fun setCacheMode(p0: Int) {
        cacheMode = p0
    }

    override fun getCacheMode() = cacheMode

    override fun setMixedContentMode(p0: Int) {
        mixedContentMode = p0
    }

    override fun getMixedContentMode() = mixedContentMode

    override fun setOffscreenPreRaster(p0: Boolean) {
        offscreenPreRaster = p0
    }

    override fun getOffscreenPreRaster() = offscreenPreRaster

    override fun setVideoOverlayForEmbeddedEncryptedVideoEnabled(p0: Boolean) {
        videoOverlayForEmbeddedEncryptedVideoEnabled = p0
    }

    override fun getVideoOverlayForEmbeddedEncryptedVideoEnabled() = videoOverlayForEmbeddedEncryptedVideoEnabled

    override fun setSafeBrowsingEnabled(p0: Boolean) {
        safeBrowsingEnabled = p0
    }

    override fun getSafeBrowsingEnabled() = safeBrowsingEnabled

    override fun setDisabledActionModeMenuItems(p0: Int) {
        disabledActionModeMenuItems = p0
    }

    override fun getDisabledActionModeMenuItems() = disabledActionModeMenuItems

    @Serializable
    // see https://chromedevtools.github.io/devtools-protocol/#/Emulation.UserAgentBrandVersion
    public data class UserAgentBrandVersion(
        val brand: String,
        val version: String,
    )

    @Serializable
    // see https://chromedevtools.github.io/devtools-protocol/#/Emulation.UserAgentMetadata
    public data class UserAgentMetadata(
        val architecture: String,
        val mobile: Boolean,
        val model: String,
        val platform: String,
        val platformVersion: String,
        val bitness: String?,
        val brands: List<UserAgentBrandVersion>?,
        val formFactors: List<String>?,
        val fullVersionList: List<UserAgentBrandVersion>?,
        val wow64: Boolean?,
        val fullVersion: String?,
    ) {
        fun toMap(): Map<String, Any?> =
            mapOf(
                BRAND_VERSION_LIST to
                    brands?.map {
                        val fullVersion = fullVersionList?.first { f -> f.brand == it.brand }?.version ?: it.version
                        arrayOf(it.brand, it.version, fullVersion)
                    },
                FULL_VERSION to fullVersion,
                PLATFORM to platform,
                PLATFORM_VERSION to platformVersion,
                ARCHITECTURE to architecture,
                MODEL to model,
                MOBILE to mobile,
                BITNESS to bitness,
                WOW64 to wow64,
                FORM_FACTORS to formFactors,
            )

        companion object {
            val BRAND_VERSION_LIST = "BRAND_VERSION_LIST"
            val FULL_VERSION = "FULL_VERSION"
            val PLATFORM = "PLATFORM"
            val PLATFORM_VERSION = "PLATFORM_VERSION"
            val ARCHITECTURE = "ARCHITECTURE"
            val MODEL = "MODEL"
            val MOBILE = "MOBILE"
            val BITNESS = "BITNESS"
            val WOW64 = "WOW64"
            val FORM_FACTORS = "FORM_FACTORS"

            fun fromMap(map: Map<String, Any?>): UserAgentMetadata {
                val brands =
                    map.get(BRAND_VERSION_LIST)?.let {
                        val array: Array<*> =
                            when (it) {
                                is List<*> -> it.toTypedArray()
                                is Array<*> -> it
                                else -> throw IllegalArgumentException("$BRAND_VERSION_LIST was ${it::class.qualifiedName}")
                            }
                        array.map { v: Any? ->
                            val array = assertStringArray(v, BRAND_VERSION_LIST)
                            if (array.size != 3) throw IllegalArgumentException("Key is not of length 3 in $BRAND_VERSION_LIST")
                            array
                        }
                    }
                val formFactors = map.get(FORM_FACTORS)?.let { assertStringArray(it, FORM_FACTORS) }
                val default = defaultUserAgentMetadata()

                return UserAgentMetadata(
                    getString(map, ARCHITECTURE) ?: default.architecture,
                    getBool(map, MOBILE) ?: default.mobile,
                    getString(map, MODEL) ?: default.model,
                    getString(map, PLATFORM) ?: default.platform,
                    getString(map, PLATFORM_VERSION) ?: default.platformVersion,
                    getString(map, BITNESS) ?: default.bitness,
                    brands?.map { UserAgentBrandVersion(it[0], it[1]) },
                    formFactors?.toList(),
                    brands?.map { UserAgentBrandVersion(it[0], it[2]) },
                    getBool(map, WOW64) ?: default.wow64,
                    getString(map, FULL_VERSION) ?: default.fullVersion,
                )
            }

            @Suppress("UNCHECKED_CAST")
            private fun assertStringArray(
                v: Any?,
                name: String,
            ): Array<String> {
                if (v is List<*>) {
                    return v.map { it as String }.toTypedArray()
                }
                if (v !is Array<*> || v.javaClass.componentType != String::class.java ||
                    v.any { it == null }
                ) {
                    val typ = v?.let { it::class.qualifiedName } ?: "null"
                    throw IllegalArgumentException("Key is not String[] in $name was $typ")
                }
                return v as Array<String>
            }

            private fun getString(
                map: Map<String, Any?>,
                key: String,
            ): String? =
                map.get(key)?.let { v ->
                    if (v is String?) v else throw IllegalArgumentException("$key was ${v::class.qualifiedName}")
                }

            private fun getBool(
                map: Map<String, Any?>,
                key: String,
            ): Boolean? =
                map.get(key)?.let { v ->
                    if (v is Boolean?) v else throw IllegalArgumentException("$key was ${v::class.qualifiedName}")
                }
        }
    }

    companion object {
        fun defaultUserAgent() = System.getProperty("http.agent")

        // TODO: Should we support basic parsing of UA? Currently if no metadata is provided, metadata and UA will mismatch
        fun defaultUserAgentMetadata() =
            UserAgentMetadata(
                "x86",
                false,
                "",
                "Windows",
                "10.0.0",
                "64",
                listOf(UserAgentBrandVersion("Chromium", "120"), UserAgentBrandVersion("Not.A/Brand", "8")),
                listOf("Desktop"),
                listOf(UserAgentBrandVersion("Chromium", "120.0.0.0"), UserAgentBrandVersion("Not.A/Brand", "8.0.0.0")),
                false,
                null,
            )
    }
}
