/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package androidx.core.net

import android.net.Uri
import java.io.File

/**
 * Creates a Uri from the given encoded URI string.
 *
 * @see Uri.parse
 */
public inline fun String.toUri(): Uri = Uri.parse(this)

/**
 * Creates a Uri from the given file.
 *
 * @see Uri.fromFile
 */
public inline fun File.toUri(): Uri = Uri.fromFile(this)

/**
 * Creates a [File] from the given [Uri]. Note that this will throw an
 * [IllegalArgumentException] when invoked on a [Uri] that lacks `file` scheme.
 */
public fun Uri.toFile(): File {
    require(scheme == "file") { "Uri lacks 'file' scheme: $this" }
    return File(requireNotNull(path) { "Uri path is null: $this" })
}
