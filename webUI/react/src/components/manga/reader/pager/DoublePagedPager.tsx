/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import Page from '../Page';
import DoublePage from '../DoublePage';

const useStyles = (settings: IReaderSettings) => makeStyles({
    preload: {
        display: 'none',
    },
    reader: {
        display: 'flex',
        flexDirection: (settings.readerType === 'DoubleLTR') ? 'row' : 'row-reverse',
        justifyContent: 'center',
        margin: '0 auto',
        width: 'auto',
        height: 'auto',
        overflowX: 'scroll',
    },
});

export default function DoublePagedPager(props: IReaderProps) {
    const {
        pages, settings, setCurPage, curPage, nextChapter, prevChapter,
    } = props;

    const classes = useStyles(settings)();

    const selfRef = useRef<HTMLDivElement>(null);
    const pagesRef = useRef<HTMLImageElement[]>([]);

    const pagesDisplayed = useRef<number>(0);
    const pageLoaded = useRef<boolean[]>(Array(pages.length).fill(false));

    function setPagesToDisplay() {
        pagesDisplayed.current = 0;
        if (curPage < pages.length && pagesRef.current[curPage]) {
            if (pageLoaded.current[curPage]) {
                pagesDisplayed.current = 1;
                const imgElem = pagesRef.current[curPage];
                const aspectRatio = imgElem.height / imgElem.width;
                if (aspectRatio < 1) {
                    return;
                }
            }
        }
        if (curPage + 1 < pages.length && pagesRef.current[curPage + 1]) {
            if (pageLoaded.current[curPage + 1]) {
                const imgElem = pagesRef.current[curPage + 1];
                const aspectRatio = imgElem.height / imgElem.width;
                if (aspectRatio < 1) {
                    return;
                }
                pagesDisplayed.current = 2;
            }
        }
    }

    function displayPages() {
        if (pagesDisplayed.current === 2) {
            ReactDOM.render(
                <DoublePage
                    key={curPage}
                    index={curPage}
                    image1src={pages[curPage].src}
                    image2src={pages[curPage + 1].src}
                    settings={settings}
                />,
                document.getElementById('display'),
            );
        } else {
            ReactDOM.render(
                <Page
                    key={curPage}
                    index={curPage}
                    src={(pagesDisplayed.current === 1) ? pages[curPage].src : ''}
                    onImageLoad={() => {}}
                    setCurPage={setCurPage}
                    settings={settings}
                />,
                document.getElementById('display'),
            );
        }
    }

    function pagesToGoBack() {
        for (let i = 1; i <= 2; i++) {
            if (curPage - i > 0 && pagesRef.current[curPage - i]) {
                if (pageLoaded.current[curPage - i]) {
                    const imgElem = pagesRef.current[curPage - i];
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

    function handleImageLoad(index: number) {
        return () => {
            pageLoaded.current[index] = true;
        };
    }

    useEffect(() => {
        const retryDisplay = setInterval(() => {
            const isLastPage = (curPage === pages.length - 1);
            if ((!isLastPage && pageLoaded.current[curPage] && pageLoaded.current[curPage + 1])
                || pageLoaded.current[curPage]) {
                setPagesToDisplay();
                displayPages();
                clearInterval(retryDisplay);
            }
        }, 50);

        document.addEventListener('keydown', keyboardControl);
        selfRef.current?.addEventListener('click', clickControl);

        return () => {
            clearInterval(retryDisplay);
            document.removeEventListener('keydown', keyboardControl);
            selfRef.current?.removeEventListener('click', clickControl);
        };
    }, [selfRef, curPage, settings.readerType]);

    return (
        <div ref={selfRef}>
            <div id="preload" className={classes.preload}>
                {
                    pages.map((page) => (
                        <img
                            ref={(e:HTMLImageElement) => { pagesRef.current[page.index] = e; }}
                            key={`${page.index}`}
                            src={page.src}
                            onLoad={handleImageLoad(page.index)}
                            alt={`${page.index}`}
                        />
                    ))
                }
            </div>
            <div id="display" className={classes.reader} />
        </div>
    );
}
