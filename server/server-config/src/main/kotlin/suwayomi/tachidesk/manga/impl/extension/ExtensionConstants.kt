package suwayomi.tachidesk.manga.impl.extension

object ExtensionsList {
    val repoMatchRegex =
        (
            "https:\\/\\/(?>www\\.|raw\\.)?(github|githubusercontent)\\.com" +
                "\\/([^\\/]+)\\/([^\\/]+)(?>(?>\\/tree|\\/blob)?\\/([^\\/\\n]*))?(?>\\/([^\\/\\n]*\\.json)?)?"
        ).toRegex()
}