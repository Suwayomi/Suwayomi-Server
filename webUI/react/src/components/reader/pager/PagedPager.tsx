/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect } from 'react';
import Page from '../Page';

const useStyles = makeStyles({
    reader: {
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'center',
        margin: '0 auto',
        width: '100%',
        height: '100vh',
    },
});

export default function PagedReader(props: IReaderProps) {
    const {
        pages, settings, setCurPage, curPage, manga, chapter,
    } = props;

    const classes = useStyles();

    function nextPage() {
        if (curPage < pages.length - 1) {
            setCurPage(curPage + 1);
        } else if (settings.loadNextonEnding) {
            window.location.href = `/manga/${manga.id}/chapter/${chapter.index + 1}`;
        }
    }

    function prevPage() {
        if (curPage > 0) { setCurPage(curPage - 1); }
    }

    function keyboardControl(e:KeyboardEvent) {
        switch (e.key) {
            case 'ArrowRight':
                nextPage();
                break;
            case 'ArrowLeft':
                prevPage();
                break;
            default:
                break;
        }
    }

    function clickControl(e:MouseEvent) {
        if (e.clientX > window.innerWidth / 2) {
            nextPage();
        } else {
            prevPage();
        }
    }

    useEffect(() => {
        document.addEventListener('keyup', keyboardControl, false);
        document.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('keyup', keyboardControl);
            document.removeEventListener('click', clickControl);
        };
    }, [curPage]);

    return (
        <div className={classes.reader}>
            <Page
                key={curPage}
                index={curPage}
                src={pages[curPage].src}
                setCurPage={setCurPage}
                settings={settings}
            />
        </div>
    );
}
