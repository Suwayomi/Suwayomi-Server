interface IExtension {
    name: string
    lang: string
    versionName: string
    iconUrl: string
    installed: boolean
    apkName: string
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
}
