/*
 * Copyright 2016 Andy Bao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.nulldev.androidcompat.util

import android.net.Uri
import java.io.File
import java.net.URI
import java.net.URISyntaxException

/**
 * Utilites to convert between Java and Android Uris.
 */
fun Uri.java(): URI =
    try {
        URI(this.toString())
    } catch (e: URISyntaxException) {
        throw IllegalArgumentException("Android Uri is not a valid java.net.URI: '$this'", e)
    }

fun Uri.file(): File {
    val scheme = this.scheme
    require(scheme == null || scheme == "file") {
        "Cannot convert non-file Uri to a File: '$this' (scheme: $scheme)"
    }
    return File(
        this.path
            ?: throw IllegalArgumentException("Uri has no path: '$this'"),
    )
}

fun URI.android(): Uri =
    Uri.parse(this.toString())
        ?: throw IllegalArgumentException("URI could not be parsed as an Android Uri: '$this'")
