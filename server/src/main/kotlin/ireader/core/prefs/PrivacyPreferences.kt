package ireader.core.prefs

import kotlinx.coroutines.flow.Flow

/**
 * Privacy preferences for managing user consent and data collection settings
 */
interface PrivacyPreferences {
    
    /**
     * Analytics and telemetry
     */
    fun analyticsEnabled(): Flow<Boolean>
    suspend fun setAnalyticsEnabled(enabled: Boolean)
    
    /**
     * Crash reporting
     */
    fun crashReportingEnabled(): Flow<Boolean>
    suspend fun setCrashReportingEnabled(enabled: Boolean)
    
    /**
     * Performance monitoring
     */
    fun performanceMonitoringEnabled(): Flow<Boolean>
    suspend fun setPerformanceMonitoringEnabled(enabled: Boolean)
    
    /**
     * Usage statistics
     */
    fun usageStatisticsEnabled(): Flow<Boolean>
    suspend fun setUsageStatisticsEnabled(enabled: Boolean)
    
    /**
     * Diagnostic data collection
     */
    fun diagnosticDataEnabled(): Flow<Boolean>
    suspend fun setDiagnosticDataEnabled(enabled: Boolean)
    
    /**
     * Automatic error reporting
     */
    fun autoErrorReportingEnabled(): Flow<Boolean>
    suspend fun setAutoErrorReportingEnabled(enabled: Boolean)
    
    /**
     * Anonymous usage tracking
     */
    fun anonymousTrackingEnabled(): Flow<Boolean>
    suspend fun setAnonymousTrackingEnabled(enabled: Boolean)
    
    /**
     * Check if user has completed privacy consent
     */
    fun hasCompletedPrivacyConsent(): Flow<Boolean>
    suspend fun setPrivacyConsentCompleted(completed: Boolean)
    
    /**
     * Get timestamp of last privacy consent
     */
    fun privacyConsentTimestamp(): Flow<Long>
    suspend fun setPrivacyConsentTimestamp(timestamp: Long)
    
    /**
     * Check if all privacy-sensitive features are disabled
     */
    suspend fun isPrivacyModeEnabled(): Boolean
    
    /**
     * Enable privacy mode (disable all data collection)
     */
    suspend fun enablePrivacyMode()
    
    /**
     * Disable privacy mode (enable default data collection)
     */
    suspend fun disablePrivacyMode()
}

/**
 * Implementation of PrivacyPreferences using DataStore
 */
class PrivacyPreferencesImpl(
    private val preferenceStore: DataStorePreferenceStore
) : PrivacyPreferences {
    
    private val analyticsEnabledPref = preferenceStore.getBoolean("privacy_analytics_enabled", false)
    private val crashReportingEnabledPref = preferenceStore.getBoolean("privacy_crash_reporting_enabled", true)
    private val performanceMonitoringEnabledPref = preferenceStore.getBoolean("privacy_performance_monitoring_enabled", false)
    private val usageStatisticsEnabledPref = preferenceStore.getBoolean("privacy_usage_statistics_enabled", false)
    private val diagnosticDataEnabledPref = preferenceStore.getBoolean("privacy_diagnostic_data_enabled", true)
    private val autoErrorReportingEnabledPref = preferenceStore.getBoolean("privacy_auto_error_reporting_enabled", false)
    private val anonymousTrackingEnabledPref = preferenceStore.getBoolean("privacy_anonymous_tracking_enabled", false)
    private val privacyConsentCompletedPref = preferenceStore.getBoolean("privacy_consent_completed", false)
    private val privacyConsentTimestampPref = preferenceStore.getLong("privacy_consent_timestamp", 0L)
    
    override fun analyticsEnabled(): Flow<Boolean> = analyticsEnabledPref.changes()
    override suspend fun setAnalyticsEnabled(enabled: Boolean) = analyticsEnabledPref.set(enabled)
    
    override fun crashReportingEnabled(): Flow<Boolean> = crashReportingEnabledPref.changes()
    override suspend fun setCrashReportingEnabled(enabled: Boolean) = crashReportingEnabledPref.set(enabled)
    
    override fun performanceMonitoringEnabled(): Flow<Boolean> = performanceMonitoringEnabledPref.changes()
    override suspend fun setPerformanceMonitoringEnabled(enabled: Boolean) = performanceMonitoringEnabledPref.set(enabled)
    
    override fun usageStatisticsEnabled(): Flow<Boolean> = usageStatisticsEnabledPref.changes()
    override suspend fun setUsageStatisticsEnabled(enabled: Boolean) = usageStatisticsEnabledPref.set(enabled)
    
    override fun diagnosticDataEnabled(): Flow<Boolean> = diagnosticDataEnabledPref.changes()
    override suspend fun setDiagnosticDataEnabled(enabled: Boolean) = diagnosticDataEnabledPref.set(enabled)
    
    override fun autoErrorReportingEnabled(): Flow<Boolean> = autoErrorReportingEnabledPref.changes()
    override suspend fun setAutoErrorReportingEnabled(enabled: Boolean) = autoErrorReportingEnabledPref.set(enabled)
    
    override fun anonymousTrackingEnabled(): Flow<Boolean> = anonymousTrackingEnabledPref.changes()
    override suspend fun setAnonymousTrackingEnabled(enabled: Boolean) = anonymousTrackingEnabledPref.set(enabled)
    
    override fun hasCompletedPrivacyConsent(): Flow<Boolean> = privacyConsentCompletedPref.changes()
    override suspend fun setPrivacyConsentCompleted(completed: Boolean) = privacyConsentCompletedPref.set(completed)
    
    override fun privacyConsentTimestamp(): Flow<Long> = privacyConsentTimestampPref.changes()
    override suspend fun setPrivacyConsentTimestamp(timestamp: Long) = privacyConsentTimestampPref.set(timestamp)
    
    override suspend fun isPrivacyModeEnabled(): Boolean {
        return !analyticsEnabledPref.get() &&
                !crashReportingEnabledPref.get() &&
                !performanceMonitoringEnabledPref.get() &&
                !usageStatisticsEnabledPref.get() &&
                !diagnosticDataEnabledPref.get() &&
                !autoErrorReportingEnabledPref.get() &&
                !anonymousTrackingEnabledPref.get()
    }
    
    override suspend fun enablePrivacyMode() {
        setAnalyticsEnabled(false)
        setCrashReportingEnabled(false)
        setPerformanceMonitoringEnabled(false)
        setUsageStatisticsEnabled(false)
        setDiagnosticDataEnabled(false)
        setAutoErrorReportingEnabled(false)
        setAnonymousTrackingEnabled(false)
    }
    
    override suspend fun disablePrivacyMode() {
        // Enable only essential features by default
        setCrashReportingEnabled(true)
        setDiagnosticDataEnabled(true)
    }
}
