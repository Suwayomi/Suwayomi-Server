/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef } from 'react';
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
        pages, settings, setCurPage, curPage, nextChapter, prevChapter,
    } = props;

    const classes = useStyles();

    const selfRef = useRef<HTMLDivElement>(null);

    function nextPage() {
        if (curPage < pages.length - 1) {
            setCurPage(curPage + 1);
        } else if (settings.loadNextonEnding) {
            nextChapter();
        }
    }

    function prevPage() {
        if (curPage > 0) {
            setCurPage(curPage - 1);
        } else {
            prevChapter();
        }
    }

    function goLeft() {
        if (settings.readerType === 'SingleLTR') {
            prevPage();
        } else if (settings.readerType === 'SingleRTL') {
            nextPage();
        }
    }

    function goRight() {
        if (settings.readerType === 'SingleLTR') {
            nextPage();
        } else if (settings.readerType === 'SingleRTL') {
            prevPage();
        }
    }

    function keyboardControl(e:KeyboardEvent) {
        switch (e.code) {
            case 'Space':
                e.preventDefault();
                nextPage();
                break;
            case 'ArrowRight':
                goRight();
                break;
            case 'ArrowLeft':
                goLeft();
                break;
            default:
                break;
        }
    }

    function clickControl(e:MouseEvent) {
        if (e.clientX > window.innerWidth / 2) {
            goRight();
        } else {
            goLeft();
        }
    }

    useEffect(() => {
        document.addEventListener('keydown', keyboardControl);
        selfRef.current?.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('keydown', keyboardControl);
            selfRef.current?.removeEventListener('click', clickControl);
        };
    }, [selfRef, curPage, settings.readerType]);

    return (
        <div ref={selfRef} className={classes.reader}>
            <Page
                key={curPage}
                index={curPage}
                onImageLoad={() => {}}
                src={pages[curPage].src}
                setCurPage={setCurPage}
                settings={settings}
            />
        </div>
    );
}
