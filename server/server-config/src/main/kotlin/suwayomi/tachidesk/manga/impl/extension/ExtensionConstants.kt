package suwayomi.tachidesk.manga.impl.extension

val repoMatchRegex =
    (
        "https:\\/\\/(?>www\\.|raw\\.)?(github|githubusercontent)\\.com" +
            "\\/([^\\/]+)\\/([^\\/]+)(?>(?>\\/tree|\\/blob)?\\/([^\\/\\n]*))?(?>\\/([^\\/\\n]*\\.json)?)?"
    ).toRegex()
