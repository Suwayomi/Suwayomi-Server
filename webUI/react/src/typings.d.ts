interface IExtension {
    name: string
    lang: string
    versionName: string
    iconUrl: string
    installed: boolean
    apkName: string
}

interface ISource {
    id: number
    name: string
    lang: string
    iconUrl: string
    supportsLatest: boolean
}
