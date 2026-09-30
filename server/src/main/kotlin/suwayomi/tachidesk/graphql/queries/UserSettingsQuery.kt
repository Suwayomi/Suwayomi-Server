package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.UserSettingsType

class UserSettingsQuery {
    @RequireAuth
    fun userSettings(
        @GraphQLIgnore
        userId: Int,
    ): UserSettingsType = UserSettingsType(userId)
}
