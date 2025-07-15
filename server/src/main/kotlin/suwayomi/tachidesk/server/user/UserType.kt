package suwayomi.tachidesk.server.user

sealed class UserType {
    class Admin(
        val id: Int,
    ) : UserType()

    data object Visitor : UserType()
}

fun UserType.requireUser(): Int =
    when (this) {
        is UserType.Admin -> id
        UserType.Visitor -> throw UnauthorizedException()
    }

class UnauthorizedException : IllegalStateException("Unauthorized")

class ForbiddenException : IllegalStateException("Forbidden")
