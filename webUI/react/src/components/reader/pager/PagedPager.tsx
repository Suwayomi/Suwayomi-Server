/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef } from 'react';
import { useHistory } from 'react-router-dom';
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
        pages, settings, setCurPage, curPageRef, manga, chapter, nextChapter,
    } = props;

    const classes = useStyles();
    const history = useHistory();

    const pageRef = useRef<HTMLDivElement>(null);

    function nextPage() {
        if (curPageRef.current < pages.length - 1) {
            setCurPage(curPageRef.current + 1);
        } else if (settings.loadNextonEnding) {
            nextChapter();
        }
    }

    function prevPage() {
        if (curPageRef.current > 0) { setCurPage(curPageRef.current - 1); }
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
        pageRef.current?.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('keyup', keyboardControl);
            pageRef.current?.removeEventListener('click', clickControl);
        };
    }, [pageRef]);

    return (
        <div ref={pageRef} className={classes.reader}>
            <Page
                key={curPageRef.current}
                index={curPageRef.current}
                src={pages[curPageRef.current].src}
                setCurPage={setCurPage}
                settings={settings}
            />
        </div>
    );
}
