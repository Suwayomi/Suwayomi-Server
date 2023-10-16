package suwayomi.tachidesk.manga.impl.track.tracker

import suwayomi.tachidesk.manga.impl.track.tracker.anilist.Anilist
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.MyAnimeList

class TrackerManager {
    companion object {
        const val MYANIMELIST = 1L
        const val ANILIST = 2L
        const val KITSU = 3L
        const val SHIKIMORI = 4L
        const val BANGUMI = 5L
        const val KOMGA = 6L
        const val MANGA_UPDATES = 7L
        const val KAVITA = 8L
        const val SUWAYOMI = 9L
    }

    val myAnimeList = MyAnimeList(MYANIMELIST)
    val aniList = Anilist(ANILIST)
//    val kitsu = Kitsu(KITSU)
//    val shikimori = Shikimori(SHIKIMORI)
//    val bangumi = Bangumi(BANGUMI)
//    val komga = Komga(KOMGA)
//    val mangaUpdates = MangaUpdates(MANGA_UPDATES)
//    val kavita = Kavita(context, KAVITA)
//    val suwayomi = Suwayomi(SUWAYOMI)

    val services: List<Tracker> = listOf(myAnimeList, aniList)

    fun getTracker(id: Long) = services.find { it.id == id }

    fun hasLoggedTracker() = services.any { it.isLoggedIn }
}
