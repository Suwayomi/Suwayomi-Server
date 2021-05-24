/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef, useState } from 'react';
import { useHistory } from 'react-router-dom';
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
        pages, settings, setCurPage, curPageRef, manga, chapter, nextChapter,
    } = props;

    const classes = useStyles();
    const history = useHistory();

    const pageRef = useRef<HTMLDivElement>(null);
    const pagesRef = useRef<HTMLDivElement[]>([]);

    function nextPage() {
        if (curPageRef.current < pages.length - 1) {
            setCurPage((page) => page + 1);
        } else if (settings.loadNextonEnding) {
            nextChapter();
        }
        pagesRef.current[curPageRef.current]?.scrollIntoView();
    }

    function prevPage() {
        if (curPageRef.current > 0) {
            setCurPage((page) => {
                const rect = pagesRef.current[page].getBoundingClientRect();
                return (rect.y < 0 && rect.y + rect.height > 0) ? page : page - 1;
            });
        }
        pagesRef.current[curPageRef.current]?.scrollIntoView();
    }

    function keyboardControl(e:KeyboardEvent) {
        switch (e.code) {
            case 'Space':
                e.preventDefault();
                nextPage();
                break;
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

    const handleLoadNextonEnding = () => {
        if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight) {
            nextChapter();
        }
    };

    useEffect(() => {
        if (settings.loadNextonEnding) { window.addEventListener('scroll', handleLoadNextonEnding); }
        document.addEventListener('keydown', keyboardControl, false);
        pageRef.current?.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('scroll', handleLoadNextonEnding);
            document.removeEventListener('keydown', keyboardControl);
            pageRef.current?.removeEventListener('click', clickControl);
        };
    }, [pageRef]);

    useEffect(() => {
        let initialPage = (chapter as IChapter).lastPageRead;
        if (initialPage > pages.length - 1) {
            initialPage = pages.length - 1;
        }
        if (initialPage > -1) {
            pagesRef.current[initialPage].scrollIntoView();
            curPageRef.current = initialPage;
            pagesRef.current[curPageRef.current].scrollIntoView();
        }
    }, []);

    return (
        <div ref={pageRef} className={classes.reader}>
            {
                pages.map((page) => (
                    <Page
                        key={page.index}
                        index={page.index}
                        src={page.src}
                        setCurPage={setCurPage}
                        settings={settings}
                        ref={(e:HTMLDivElement) => pagesRef.current.push(e)}
                    />
                ))
            }
        </div>
    );
}
