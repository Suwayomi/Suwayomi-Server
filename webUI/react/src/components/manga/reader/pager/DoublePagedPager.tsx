/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef } from 'react';
import Page from '../Page';

const useStyles = (settings: IReaderSettings) => makeStyles({
    reader: {
        display: 'flex',
        flexDirection: (settings.readerType === 'DoubleLTR') ? 'row' : 'row-reverse',
        justifyContent: 'center',
        margin: '0 auto',
        width: 'auto',
        height: 'auto',
        minHeight: '99vh',
        overflowX: 'scroll',
    },
});

export default function DoublePagedReader(props: IReaderProps) {
    const {
        pages, settings, setCurPage, curPage, nextChapter, prevChapter,
    } = props;

    const classes = useStyles(settings)();

    const selfRef = useRef<HTMLDivElement>(null);
    const pagesRef = useRef<HTMLDivElement[]>([]);

    const pagesDisplayed = useRef<number>(0);

    function pagesToGoBack() {
        for (let i = 1; i <= 2; i++) {
            if (curPage - i > 0 && pagesRef.current[curPage - i]) {
                if (pagesRef.current[curPage - i].children[0] instanceof HTMLImageElement) {
                    const imgElem = pagesRef.current[curPage - i].children[0] as HTMLImageElement;
                    const aspectRatio = imgElem.height / imgElem.width;
                    if (aspectRatio < 1) {
                        return 1;
                    }
                }
            }
        }
        return 2;
    }

    function nextPage() {
        if (curPage < pages.length - 1) {
            const nextCurPage = curPage + pagesDisplayed.current;
            setCurPage((nextCurPage >= pages.length) ? pages.length - 1 : nextCurPage);
        } else if (settings.loadNextonEnding) {
            nextChapter();
        }
    }

    function prevPage() {
        if (curPage > 0) {
            const nextCurPage = curPage - pagesToGoBack();
            setCurPage((nextCurPage < 0) ? 0 : nextCurPage);
        } else {
            prevChapter();
        }
    }

    function goLeft() {
        if (settings.readerType === 'DoubleLTR') {
            prevPage();
        } else {
            nextPage();
        }
    }

    function goRight() {
        if (settings.readerType === 'DoubleLTR') {
            nextPage();
        } else {
            prevPage();
        }
    }

    function setPagesToDisplay() {
        pagesDisplayed.current = 2;
        if (curPage < pages.length && pagesRef.current[curPage]) {
            if (pagesRef.current[curPage].children[0] instanceof HTMLImageElement) {
                pagesDisplayed.current = 1;
                const imgElem = pagesRef.current[curPage].children[0] as HTMLImageElement;
                const aspectRatio = imgElem.height / imgElem.width;
                if (aspectRatio < 1) {
                    return;
                }
            }
        }
        if (curPage + 1 < pages.length && pagesRef.current[curPage + 1]) {
            if (pagesRef.current[curPage + 1].children[0] instanceof HTMLImageElement) {
                const imgElem = pagesRef.current[curPage + 1].children[0] as HTMLImageElement;
                const aspectRatio = imgElem.height / imgElem.width;
                if (aspectRatio < 1) {
                    return;
                }
                pagesDisplayed.current = 2;
            }
        }
    }

    function showPages() {
        // console.log(`pages to display: ${pagesDisplayed.current}`);
        for (let i = 0; i < pagesDisplayed.current; i++) {
            if (curPage + i < pages.length) {
                // console.log(`showing page ${curPage + i}`);
                pagesRef.current[curPage + i].style.display = 'block';
            }
        }
    }

    function hidePages() {
        for (let i = 0; i < pagesDisplayed.current; i++) {
            // console.log(`hiding page ${curPage + i}`);
            if (pagesRef.current[curPage + i]) {
                pagesRef.current[curPage + i].style.display = 'none';
            }
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
        pagesRef.current.forEach((e) => {
            const pageRef = e;
            pageRef.style.display = 'none';
        });
    }, []);

    useEffect(() => {
        setPagesToDisplay();
        showPages();
        document.addEventListener('keydown', keyboardControl);
        selfRef.current?.addEventListener('click', clickControl);

        return () => {
            hidePages();
            document.removeEventListener('keydown', keyboardControl);
            selfRef.current?.removeEventListener('click', clickControl);
        };
    }, [selfRef, curPage, settings.readerType]);

    return (
        <div ref={selfRef} className={classes.reader}>
            <div id="test" />
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
