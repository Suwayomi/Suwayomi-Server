package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated

enum class AuthMode {
    NONE,
    @GraphQLDeprecated("Please switch to another AuthMode")
    BASIC_AUTH,
    SIMPLE_LOGIN,
    UI_LOGIN,
    ;

    companion object {
        fun from(channel: String): AuthMode = entries.find { it.name.equals(channel, ignoreCase = true) } ?: NONE
    }
}
