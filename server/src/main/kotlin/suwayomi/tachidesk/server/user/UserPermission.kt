package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable

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
                // ACCESS_NSFW,
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

/**
 * Throws [ForbiddenException] when [requested] contains any permission that the issuer
 * ([issuerId]) does not currently hold, i.e. requires `requested ⊆ issuer's effective
 * permissions` (the ADMIN role holds every permission, so it always passes; an empty
 * [requested] always passes).
 *
 * Used whenever a user's permissions are delegated (registering a user, issuing a
 * registration code, editing another user's permissions) so a privileged-but-not-admin
 * user such as a MANAGE_USERS holder cannot mint permissions beyond their own.
 */
fun assertPermissionsAssignable(
    issuerId: Int,
    requested: Collection<UserPermission>,
) {
    val overGrant = requested.filterNot { hasPermission(issuerId, it) }
    if (overGrant.isNotEmpty()) {
        throw ForbiddenException(
            "Forbidden: cannot delegate permissions the issuer does not hold: $overGrant",
        )
    }
}

/**
 * Returns true if the database user [userId] has [permission] (the ADMIN role bypasses).
 * Used by paths that run without a [UserType] in context.
 */
fun hasPermission(
    userId: Int,
    permission: UserPermission,
): Boolean =
    transaction {
        val isAdmin =
            UserRolesTable
                .selectAll()
                .where { UserRolesTable.user eq userId }
                .any { it[UserRolesTable.role].equals(UserRole.ADMIN.name, ignoreCase = true) }
        if (isAdmin) return@transaction true

        UserPermissionsTable
            .selectAll()
            .where {
                (UserPermissionsTable.user eq userId) and
                    (UserPermissionsTable.permission eq permission.name)
            }.count() > 0
    }
