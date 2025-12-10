package ireader.core.source

import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.MovieUrl
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime

class TestSource : ireader.core.source.CatalogSource {
    override val id = 1L

    override val name = "Test source"
    override val lang get() = "en"
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        delay(1000)
        val noHipstersOffset = 10
        val picId = manga.title.split(" ")[1].toInt() + noHipstersOffset
        return manga.copy(cover = "https://picsum.photos/300/400/?image=$picId")
    }

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        delay(1000)
        return MangasPageInfo(getTestManga(page), true)
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        var mangaList = getTestManga(page)

        filters.forEach { filter ->
            if (filter is Filter.Title) {
                mangaList = mangaList.filter { filter.value in it.title }
            }
        }

        return MangasPageInfo(mangaList, true)
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        delay(1000)
        return getTestChapters()
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        delay(1000)
        if (chapter.key == "4") {
            return listOf(MovieUrl("https://storage.googleapis.com/exoplayer-test-media-1/mp4/dizzy-with-tx3g.mp4"))
        }
        return getTestPages()
    }

    class Alphabetically : Listing("Alphabetically")

    class Latest : Listing("Latest")

    override fun getListings(): List<Listing> {
        return listOf(Alphabetically(), Latest())
    }

    override fun getFilters(): FilterList {
        return listOf(
            Filter.Title(),
            Filter.Author(),
            Filter.Artist(),
            GenreList(getGenreList())
        )
    }

    override fun getCommands(): CommandList {
        return listOf(
            Command.Chapter.Note("NOTE:Only the first value would be used"),
            Command.Chapter.Text("Title"),
            Command.Chapter.Select(
                "Options",
                arrayOf(
                    "None",
                    "Last 10 Chapter"
                )
            ),
        )
    }
    private class GenreList(genres: List<Filter.Genre>) : Filter.Group("Genres", genres)

    private fun getGenreList() = listOf(
        Filter.Genre("4-koma"),
        Filter.Genre("Action"),
        Filter.Genre("Adventure"),
        Filter.Genre("Award Winning"),
        Filter.Genre("Comedy"),
        Filter.Genre("Cooking"),
        Filter.Genre("Doujinshi"),
        Filter.Genre("Drama"),
        Filter.Genre("Ecchi"),
        Filter.Genre("Fantasy"),
        Filter.Genre("Gender Bender"),
        Filter.Genre("Harem"),
        Filter.Genre("Historical"),
        Filter.Genre("Horror"),
        Filter.Genre("Josei"),
        Filter.Genre("Martial Arts"),
        Filter.Genre("Mecha"),
        Filter.Genre("Medical"),
        Filter.Genre("Music"),
        Filter.Genre("Mystery"),
        Filter.Genre("Oneshot"),
        Filter.Genre("Psychological"),
        Filter.Genre("Romance"),
        Filter.Genre("School Life"),
        Filter.Genre("Sci-Fi"),
        Filter.Genre("Seinen"),
        Filter.Genre("Shoujo"),
        Filter.Genre("Shoujo Ai"),
        Filter.Genre("Shounen"),
        Filter.Genre("Shounen Ai"),
        Filter.Genre("Slice of Life"),
        Filter.Genre("Smut"),
        Filter.Genre("Sports"),
        Filter.Genre("Supernatural"),
        Filter.Genre("Tragedy"),
        Filter.Genre("Webtoon"),
        Filter.Genre("Yaoi"),
        Filter.Genre("Yuri"),
        Filter.Genre("[no chapters]"),
        Filter.Genre("Game"),
        Filter.Genre("Isekai")
    )

    private fun getTestManga(page: Int): List<MangaInfo> {
        val list = mutableListOf<MangaInfo>()
        val id = (page - 1) * 20 + 1
        val manga1 = MangaInfo(
            "$id",
            "Manga $id",
            "Artist $id",
            "Author $id",
            "Lorem ipsum",
            listOf("Foo", "Bar"),
            0,
            ""
        )
        list += manga1

        for (i in 1..19) {
            list += manga1.copy(key = "${id + i}", title = "Manga ${id + i}")
        }

        return list
    }
    @OptIn(ExperimentalTime::class)
    private fun getTestChapters(commands: List<Command<*>>): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()

        return when (val cmd = commands.firstOrNull()) {
            is Command.Chapter.Text -> {
                chapters.add(
                    ChapterInfo(
                        kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                        cmd.value,
                        kotlin.time.Clock.System.now().toEpochMilliseconds()
                    )
                )
                chapters
            }
            is Command.Chapter.Select -> {

                chapters.add(
                    ChapterInfo(
                        kotlin.time.Clock.System.now().toEpochMilliseconds().toString(),
                        "Chapter ${cmd.options[cmd.value]}",
                        kotlin.time.Clock.System.now().toEpochMilliseconds()
                    )
                )
                chapters
            }
            else -> {
                val chapter1 = ChapterInfo(
                    "1",
                    "Chapter 1",
                    kotlin.time.Clock.System.now().toEpochMilliseconds()
                )
                val chapter2 = chapter1.copy(key = "2", name = "Chapter2")
                val chapter3 = chapter1.copy(key = "3", name = "Chapter3")

                return listOf(chapter1, chapter2, chapter3)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun getTestChapters(): List<ChapterInfo> {
        val chapter1 = ChapterInfo(
            "1",
            "Chapter 1",
            kotlin.time.Clock.System.now().toEpochMilliseconds()
        )
        val chapter2 = chapter1.copy(key = "2", name = "Chapter2")
        val chapter3 = chapter1.copy(key = "3", name = "Chapter3")
        val chapter4 = chapter1.copy(key = "4", name = "Chapter4", type = ChapterInfo.MOVIE)

        return listOf(chapter1, chapter2, chapter3,chapter4)
    }

    private fun getTestPages(): List<Page> {
        val pages = mutableListOf<Page>()
        for(i in 1..3)  {
            pages.add(ImageUrl("https://picsum.photos/300/400/?image=1"))
        }

        ipsum.split(",").map {
            pages.add(Text(it))
        }

        return pages
    }
    val ipsum = """
  Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin eget rutrum nisl, vitae ullamcorper velit. In eu diam justo. Sed placerat, risus at luctus vestibulum, nisl nunc lobortis est, eu viverra sapien nulla at metus. Proin lacinia felis tortor. Nullam quis dignissim lorem. Morbi vel tristique mi. Phasellus eget tellus dui. Maecenas sollicitudin in neque vel porta. Nam vel nulla at lectus fringilla fringilla mollis at metus. Duis elit nulla, viverra gravida ullamcorper sit amet, sagittis vitae eros. Interdum et malesuada fames ac ante ipsum primis in faucibus. Proin sodales vehicula mi, id laoreet elit dapibus in. Morbi tristique velit a velit pellentesque aliquam. Etiam tempus venenatis blandit.

  Nullam ac ornare sapien, in blandit risus. Ut nec lectus ac massa ultricies commodo nec ut enim. Morbi aliquet dapibus aliquet. Vestibulum feugiat, eros vel elementum condimentum, justo ex ultrices mi, ut mollis elit tortor et est. Morbi pulvinar ante sit amet ante mattis placerat. Etiam vulputate arcu id magna vestibulum interdum. Quisque aliquam non dui finibus luctus. Interdum et malesuada fames ac ante ipsum primis in faucibus. Aenean dictum sapien non arcu porta, quis auctor tortor lacinia. Phasellus id tellus eget nisi pellentesque lacinia. Donec vitae arcu ut magna sollicitudin hendrerit sit amet ut tellus. Mauris tincidunt, quam quis imperdiet ultrices, lacus augue molestie est, eget condimentum felis augue eleifend mi. In in aliquam ex. Vestibulum tristique ornare neque, vitae ultrices metus luctus vitae. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Sed vitae eros interdum, pulvinar turpis at, pharetra nulla.

  Nulla vitae egestas nisl, at pharetra arcu. Praesent vulputate congue mauris, blandit venenatis sapien porttitor sit amet. Etiam pretium ex at diam pulvinar aliquet. Nunc ornare, quam sit amet malesuada lobortis, metus ligula eleifend nisi, vel pulvinar arcu mauris non sem. Pellentesque pretium nisi dui, et faucibus odio interdum sit amet. Praesent quis sem nunc. Quisque convallis ornare mauris id maximus. Phasellus facilisis sem posuere iaculis imperdiet. Curabitur viverra eleifend arcu at mattis. Pellentesque sollicitudin metus erat, eu tincidunt eros porta a. Fusce consequat porta nulla.

  Aenean egestas blandit erat, et lobortis dui eleifend nec. Integer dapibus nunc mattis nisi vestibulum tempus. Quisque aliquam ante sit amet eros aliquam, in cursus neque ultricies. Phasellus egestas nulla nec lorem dictum efficitur. Ut vehicula metus id vehicula tempor. Proin interdum mollis lectus, et posuere lorem. Integer vitae sodales justo. Nam finibus quis ante a dapibus. Nullam mattis eleifend leo, nec venenatis leo sagittis vitae. Duis dictum semper varius. Maecenas mollis maximus eros, sit amet dictum enim pharetra et. Sed eget neque at diam accumsan imperdiet cursus vel tellus. Curabitur vel luctus nulla, id aliquet mauris.

  Pellentesque consequat metus felis, sit amet commodo sapien vestibulum et. Curabitur velit dolor, lacinia et interdum in, viverra ut ante. Curabitur malesuada odio et condimentum mattis. Sed aliquam leo dui, eu fringilla mi laoreet in. Donec at justo id sem egestas dictum eget aliquet ex. Curabitur iaculis facilisis nisl, at efficitur est tincidunt a. Maecenas ullamcorper sapien vel pulvinar posuere. Aenean molestie at quam in convallis. Praesent vitae odio mauris. Suspendisse metus urna, congue ut orci laoreet, feugiat vestibulum ipsum.
    """.trimIndent()
}
