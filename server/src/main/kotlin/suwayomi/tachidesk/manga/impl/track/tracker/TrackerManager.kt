package suwayomi.tachidesk.manga.impl.track.tracker

import suwayomi.tachidesk.manga.impl.track.tracker.anilist.Anilist
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.Bangumi
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.Kitsu
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.MangaUpdates
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.MyAnimeList
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.Shikimori

object TrackerManager {
    const val MYANIMELIST = 1
    const val ANILIST = 2
    const val KITSU = 3
    const val SHIKIMORI = 4
    const val BANGUMI = 5

    // Historical tracker ids kept reserved so existing records/accounts keep
    // their meaning; there is no implementation behind them anymore.
    const val KOMGA = 6
    const val MANGA_UPDATES = 7
    const val KAVITA = 8
    const val SUWAYOMI = 9

    val myAnimeList = MyAnimeList(MYANIMELIST)
    val aniList = Anilist(ANILIST)

    val kitsu = Kitsu(KITSU)

    val shikimori = Shikimori(SHIKIMORI)
    val bangumi = Bangumi(BANGUMI)

    val mangaUpdates = MangaUpdates(MANGA_UPDATES)

    val services: List<Tracker> = listOf(myAnimeList, aniList, kitsu, mangaUpdates, shikimori, bangumi)

    /**
     * Returns the tracker for [id], or null when the id is unknown or belongs
     * to a removed tracker. Track records pointing at a null-returning id are
     * orphaned: they stay readable locally (the row is not deleted) but every
     * remote operation on them is skipped (see `trackChapterForTracker`) —
     * they can only be unbound.
     */
    fun getTracker(id: Int) = services.find { it.id == id }

    fun hasLoggedTracker(userId: Int) = services.any { it.isLoggedIn(userId) }
}
