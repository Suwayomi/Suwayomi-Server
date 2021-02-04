/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

interface IExtension {
    name: string
    lang: string
    versionName: string
    iconUrl: string
    installed: boolean
    apkName: string
    pkgName: string
}

interface ISource {
    id: string
    name: string
    lang: string
    iconUrl: string
    supportsLatest: boolean
    history: any
}

interface IManga {
    id: number
    title: string
    thumbnailUrl: string
}

interface IChapter {
    id: number
    url: string
    name: string
    date_upload: number
    chapter_number: number
    scanlator: String
    mangaId: number
    pageCount: number
}
