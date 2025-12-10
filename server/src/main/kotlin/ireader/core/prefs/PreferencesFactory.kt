package ireader.core.prefs

expect class PreferenceStoreFactory {
    fun create(vararg names: String): PreferenceStore
}