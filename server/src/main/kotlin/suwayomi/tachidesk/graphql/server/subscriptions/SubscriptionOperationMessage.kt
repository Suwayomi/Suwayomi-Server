/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * The `graphql-ws` protocol from Apollo Client has some special text messages to signal events.
 * Along with the HTTP WebSocket event handling we need to have some extra logic
 *
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SubscriptionOperationMessage(
    val type: String,
    val id: String? = null,
    val payload: Any? = null,
) {
    enum class CommonMessages(val type: String) {
        GQL_PING("ping"),
        GQL_PONG("pong"),
        GQL_COMPLETE("complete"),
    }

    enum class ClientMessages(val type: String) {
        GQL_CONNECTION_INIT("connection_init"),
        GQL_SUBSCRIBE("subscribe"),
    }

    enum class ServerMessages(val type: String) {
        GQL_CONNECTION_ACK("connection_ack"),
        GQL_NEXT("next"),
        GQL_ERROR("error"),
    }
}
