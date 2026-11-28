package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.IOException

/** Fails an image call while a host's circuit is open; the last real transport failure rides along as the cause. */
class HostCircuitOpenException(
    host: String,
    cause: IOException?,
) : IOException("host circuit open: $host", cause)
