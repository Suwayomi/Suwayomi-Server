package xyz.nulldev.androidcompat.webkit

import android.webkit.WebSettings

class WebSettingsImpl : WebSettings() {
    // Boolean settings
    private var mediaPlaybackRequiresUserGesture = true
    private var builtInZoomControls = false
    private var displayZoomControls = true
    private var allowFileAccess = true
    private var allowContentAccess = true
    private var loadWithOverviewMode = false
    private var enableSmoothTransition = false
    private var saveFormData = true
    private var savePassword = false
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
    override fun setSupportZoom(p0: Boolean) { mediaPlaybackRequiresUserGesture = p0 }
    override fun supportZoom() = mediaPlaybackRequiresUserGesture

    override fun setMediaPlaybackRequiresUserGesture(p0: Boolean) { mediaPlaybackRequiresUserGesture = p0 }
    override fun getMediaPlaybackRequiresUserGesture() = mediaPlaybackRequiresUserGesture

    override fun setBuiltInZoomControls(p0: Boolean) { builtInZoomControls = p0 }
    override fun getBuiltInZoomControls() = builtInZoomControls

    override fun setDisplayZoomControls(p0: Boolean) { displayZoomControls = p0 }
    override fun getDisplayZoomControls() = displayZoomControls

    override fun setAllowFileAccess(p0: Boolean) { allowFileAccess = p0 }
    override fun getAllowFileAccess() = allowFileAccess

    override fun setAllowContentAccess(p0: Boolean) { allowContentAccess = p0 }
    override fun getAllowContentAccess() = allowContentAccess

    override fun setLoadWithOverviewMode(p0: Boolean) { loadWithOverviewMode = p0 }
    override fun getLoadWithOverviewMode() = loadWithOverviewMode

    override fun setEnableSmoothTransition(p0: Boolean) { enableSmoothTransition = p0 }
    override fun enableSmoothTransition() = enableSmoothTransition

    override fun setSaveFormData(p0: Boolean) { saveFormData = p0 }
    override fun getSaveFormData() = saveFormData

    override fun setSavePassword(p0: Boolean) { savePassword = p0 }
    override fun getSavePassword() = savePassword

    override fun setTextZoom(p0: Int) { textZoom = p0 }
    override fun getTextZoom() = textZoom

    override fun setDefaultZoom(p0: ZoomDensity?) { defaultZoom = p0 }
    override fun getDefaultZoom() = defaultZoom

    override fun setLightTouchEnabled(p0: Boolean) { lightTouchEnabled = p0 }
    override fun getLightTouchEnabled() = lightTouchEnabled

    override fun setUseWideViewPort(p0: Boolean) { useWideViewPort = p0 }
    override fun getUseWideViewPort() = useWideViewPort

    override fun setSupportMultipleWindows(p0: Boolean) { supportMultipleWindows = p0 }
    override fun supportMultipleWindows() = supportMultipleWindows

    override fun setLayoutAlgorithm(p0: LayoutAlgorithm?) { layoutAlgorithm = p0 }
    override fun getLayoutAlgorithm() = layoutAlgorithm

    override fun setStandardFontFamily(p0: String?) { standardFontFamily = p0 }
    override fun getStandardFontFamily() = standardFontFamily

    override fun setFixedFontFamily(p0: String?) { fixedFontFamily = p0 }
    override fun getFixedFontFamily() = fixedFontFamily

    override fun setSansSerifFontFamily(p0: String?) { sansSerifFontFamily = p0 }
    override fun getSansSerifFontFamily() = sansSerifFontFamily

    override fun setSerifFontFamily(p0: String?) { serifFontFamily = p0 }
    override fun getSerifFontFamily() = serifFontFamily

    override fun setCursiveFontFamily(p0: String?) { cursiveFontFamily = p0 }
    override fun getCursiveFontFamily() = cursiveFontFamily

    override fun setFantasyFontFamily(p0: String?) { fantasyFontFamily = p0 }
    override fun getFantasyFontFamily() = fantasyFontFamily

    override fun setMinimumFontSize(p0: Int) { minimumFontSize = p0 }
    override fun getMinimumFontSize() = minimumFontSize

    override fun setMinimumLogicalFontSize(p0: Int) { minimumLogicalFontSize = p0 }
    override fun getMinimumLogicalFontSize() = minimumLogicalFontSize

    override fun setDefaultFontSize(p0: Int) { defaultFontSize = p0 }
    override fun getDefaultFontSize() = defaultFontSize

    override fun setDefaultFixedFontSize(p0: Int) { defaultFixedFontSize = p0 }
    override fun getDefaultFixedFontSize() = defaultFixedFontSize

    override fun setLoadsImagesAutomatically(p0: Boolean) { loadsImagesAutomatically = p0 }
    override fun getLoadsImagesAutomatically() = loadsImagesAutomatically

    override fun setBlockNetworkImage(p0: Boolean) { blockNetworkImage = p0 }
    override fun getBlockNetworkImage() = blockNetworkImage

    override fun setBlockNetworkLoads(p0: Boolean) { blockNetworkLoads = p0 }
    override fun getBlockNetworkLoads() = blockNetworkLoads

    override fun setJavaScriptEnabled(p0: Boolean) { javaScriptEnabled = p0 }
    override fun getJavaScriptEnabled() = javaScriptEnabled

    override fun setAllowUniversalAccessFromFileURLs(p0: Boolean) { allowUniversalAccessFromFileURLs = p0 }
    override fun getAllowUniversalAccessFromFileURLs() = allowUniversalAccessFromFileURLs

    override fun setAllowFileAccessFromFileURLs(p0: Boolean) { allowFileAccessFromFileURLs = p0 }
    override fun getAllowFileAccessFromFileURLs() = allowFileAccessFromFileURLs

    override fun setPluginState(p0: PluginState?) { pluginState = p0 }
    override fun getPluginState() = pluginState

    override fun setDatabasePath(p0: String?) { databasePath = p0 }
    override fun getDatabasePath() = databasePath ?: ""

    override fun setGeolocationDatabasePath(p0: String?) { geolocationDatabasePath = p0 }

    override fun setAppCacheEnabled(p0: Boolean) {}
    override fun setAppCachePath(p0: String?) { appCachePath = p0 }

    override fun setAppCacheMaxSize(p0: Long) { appCacheMaxSize = p0 }

    override fun setDatabaseEnabled(p0: Boolean) {}
    override fun getDatabaseEnabled() = true

    override fun setDomStorageEnabled(p0: Boolean) { domStorageEnabled = p0 }
    override fun getDomStorageEnabled() = domStorageEnabled

    override fun setGeolocationEnabled(p0: Boolean) { geolocationEnabled = p0 }

    override fun setJavaScriptCanOpenWindowsAutomatically(p0: Boolean) { javaScriptCanOpenWindowsAutomatically = p0 }
    override fun getJavaScriptCanOpenWindowsAutomatically() = javaScriptCanOpenWindowsAutomatically

    override fun setDefaultTextEncodingName(p0: String?) { defaultTextEncodingName = p0 }
    override fun getDefaultTextEncodingName() = defaultTextEncodingName ?: ""

    override fun setUserAgentString(p0: String?) { userAgentString = p0 }
    override fun getUserAgentString() = userAgentString ?: ""

    override fun setNeedInitialFocus(p0: Boolean) { needInitialFocus = p0 }

    override fun setRenderPriority(p0: RenderPriority?) { renderPriority = p0 }

    override fun setCacheMode(p0: Int) { cacheMode = p0 }
    override fun getCacheMode() = cacheMode

    override fun setMixedContentMode(p0: Int) { mixedContentMode = p0 }
    override fun getMixedContentMode() = mixedContentMode

    override fun setOffscreenPreRaster(p0: Boolean) { offscreenPreRaster = p0 }
    override fun getOffscreenPreRaster() = offscreenPreRaster

    override fun setSafeBrowsingEnabled(p0: Boolean) { safeBrowsingEnabled = p0 }
    override fun getSafeBrowsingEnabled() = safeBrowsingEnabled

    override fun setDisabledActionModeMenuItems(p0: Int) { disabledActionModeMenuItems = p0 }
    override fun getDisabledActionModeMenuItems() = disabledActionModeMenuItems
}
