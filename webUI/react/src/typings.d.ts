/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

interface IExtension {
    name: string
    pkgName: string
    versionName: string
    versionCode: number
    lang: string
    isNsfw: boolean
    apkName: string
    iconUrl: string
    installed: boolean
    hasUpdate: boolean
    obsolete: boolean
}

interface ISource {
    id: string
    name: string
    lang: string
    iconUrl: string
    supportsLatest: boolean
    history: any
}

interface IMangaCard {
    id: number
    title: string
    thumbnailUrl: string
}

interface IManga {
    id: number
    sourceId: string

    url: string
    title: string
    thumbnailUrl: string

    artist: string
    author: string
    description: string
    genre: string
    status: string

    inLibrary: boolean
    source: ISource
}

interface IChapter {
    id: number
    url: string
    name: string
    date_upload: number
    chapter_number: number
    scanlator: String
    mangaId: number
    chapterIndex: number
    chapterCount: number
    pageCount: number
}

interface IPartialChpter {
    pageCount: number
    chapterIndex: number
    chapterCount: number
}

interface ICategory {
    id: number
    order: number
    name: String
    isLanding: boolean
}

interface INavbarOverride {
    status: boolean
    value: any
}
