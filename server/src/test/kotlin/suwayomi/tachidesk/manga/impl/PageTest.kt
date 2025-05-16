package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.Page.getPageName
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.assertEquals

class PageTest : ApplicationTest() {
    @Test
    fun testGetPageName() {
        val tests = listOf(0 to 5, 1 to 5, 2 to 5, 100 to 100, 998 to 1000, 1400 to 1500)

        val testResults =
            tests.map { (page, count) ->
                getPageName(page, count)
            }

        assertEquals(testResults[0], "001")
        assertEquals(testResults[1], "002")
        assertEquals(testResults[2], "003")
        assertEquals(testResults[3], "101")
        assertEquals(testResults[4], "0999")
        assertEquals(testResults[5], "1401")
    }
}
