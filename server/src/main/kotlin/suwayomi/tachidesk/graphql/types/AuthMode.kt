package suwayomi.tachidesk.graphql.types

enum class AuthMode {
    NONE,
    BASIC_AUTH,
    SIMPLE_LOGIN,
    UI_LOGIN,
    // TODO: ACCOUNT for #623
    ;

    companion object {
        fun from(channel: String): AuthMode = entries.find { it.name.lowercase() == channel.lowercase() } ?: NONE
    }
}
