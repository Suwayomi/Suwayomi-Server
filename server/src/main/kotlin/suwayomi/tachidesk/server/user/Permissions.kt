package suwayomi.tachidesk.server.user

enum class Permissions {
    INSTALL_EXTENSIONS,
    INSTALL_UNTRUSTED_EXTENSIONS,
    UNINSTALL_EXTENSIONS,
    DOWNLOAD_CHAPTERS,
    CREATE_USER,
    NSFW,
    ;

    companion object {
        val defaultPermissions =
            setOf(
                INSTALL_EXTENSIONS,
                INSTALL_UNTRUSTED_EXTENSIONS,
                UNINSTALL_EXTENSIONS,
                DOWNLOAD_CHAPTERS,
                NSFW,
            )
    }
}

/**
 * Returns true if [permission] is contained in this list of permissions.
 */
fun List<Permissions>.hasPermission(permission: Permissions): Boolean = permission in this
