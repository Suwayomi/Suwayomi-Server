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
        val tests = listOf(0, 1, 2, 100)

        val testResults =
            tests.map {
                getPageName(it)
            }

        assertEquals(testResults[0], "001")
        assertEquals(testResults[1], "002")
        assertEquals(testResults[2], "003")
        assertEquals(testResults[3], "101")
    }
}
