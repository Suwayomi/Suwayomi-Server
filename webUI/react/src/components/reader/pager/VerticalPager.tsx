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
        flexDirection: 'column',
        justifyContent: 'center',
        margin: '0 auto',
        width: '100%',
    },
});

export default function VerticalReader(props: IReaderProps) {
    const {
        pages, settings, setCurPage, curPage, chapter, nextChapter, prevChapter,
    } = props;

    const classes = useStyles();

    const selfRef = useRef<HTMLDivElement>(null);
    const pagesRef = useRef<HTMLDivElement[]>([]);

    function nextPage() {
        if (curPage < pages.length - 1) {
            pagesRef.current[curPage + 1]?.scrollIntoView();
            setCurPage((page) => page + 1);
        } else if (settings.loadNextonEnding) {
            nextChapter();
        }
    }

    function prevPage() {
        if (curPage > 0) {
            const rect = pagesRef.current[curPage].getBoundingClientRect();
            if (rect.y < 0 && rect.y + rect.height > 0) {
                pagesRef.current[curPage]?.scrollIntoView();
            } else {
                pagesRef.current[curPage - 1]?.scrollIntoView();
                setCurPage(curPage - 1);
            }
        } else if (curPage === 0) {
            prevChapter();
        }
    }

    function goLeft() {
        if (settings.readerDirection === 'LeftToRight') {
            prevPage();
        } else {
            nextPage();
        }
    }

    function goRight() {
        if (settings.readerDirection === 'LeftToRight') {
            nextPage();
        } else {
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

    const handleLoadNextonEnding = () => {
        if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight) {
            nextChapter();
        }
    };

    useEffect(() => {
        if (settings.loadNextonEnding) { document.addEventListener('scroll', handleLoadNextonEnding); }
        document.addEventListener('keydown', keyboardControl, false);
        selfRef.current?.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('scroll', handleLoadNextonEnding);
            document.removeEventListener('keydown', keyboardControl);
            selfRef.current?.removeEventListener('click', clickControl);
        };
    }, [selfRef, curPage, settings.readerDirection]);

    useEffect(() => {
        // scroll last read page into view
        let initialPage = (chapter as IChapter).lastPageRead;
        if (initialPage > pages.length - 1) {
            initialPage = pages.length - 1;
        }
        if (initialPage > -1) {
            pagesRef.current[initialPage].scrollIntoView();
        }
    }, []);

    return (
        <div ref={selfRef} className={classes.reader}>
            {
                pages.map((page) => (
                    <Page
                        key={page.index}
                        index={page.index}
                        src={page.src}
                        setCurPage={setCurPage}
                        settings={settings}
                        ref={(e:HTMLDivElement) => { pagesRef.current[page.index] = e; }}
                    />
                ))
            }
        </div>
    );
}
