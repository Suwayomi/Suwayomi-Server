package suwayomi.tachidesk.server.user

enum class UserPermission {
    INSTALL_EXTENSIONS,
    INSTALL_EXTERNAL_EXTENSIONS,
    UNINSTALL_EXTENSIONS,
    DOWNLOAD_CHAPTERS,
    ACCESS_NSFW,
    MANAGE_SETTINGS,
    MANAGE_USERS,
    MANAGE_EXTENSION_STORES,
    MANAGE_SOURCE_PREFERENCES,
    MANAGE_CACHE,
    ;

    companion object {
        val defaultPermissions =
            setOf(
                INSTALL_EXTENSIONS,
                INSTALL_EXTERNAL_EXTENSIONS,
                UNINSTALL_EXTENSIONS,
                DOWNLOAD_CHAPTERS,
                ACCESS_NSFW,
            )
    }
}

/**
 * Returns true if [permission] is contained in this list of permissions.
 */
fun List<UserPermission>.hasPermission(permission: UserPermission): Boolean = permission in this

fun UserType.hasPermission(permission: UserPermission): Boolean =
    when (this) {
        is UserType.Admin -> true
        is UserType.User -> permission in permissions
        UserType.Visitor -> false
    }
