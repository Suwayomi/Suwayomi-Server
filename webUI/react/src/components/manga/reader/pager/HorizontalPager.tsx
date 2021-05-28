/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/* eslint-disable no-lonely-if */

import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef } from 'react';
import Page from '../Page';

const useStyles = (settings: IReaderSettings) => makeStyles({
    reader: {
        display: 'flex',
        flexDirection: (settings.readerType === 'ContinuesHorizontalLTR') ? 'row' : 'row-reverse',
        justifyContent: (settings.readerType === 'ContinuesHorizontalLTR') ? 'flex-start' : 'flex-end',
        margin: '0 auto',
        width: 'auto',
        height: 'auto',
        overflowX: 'visible',
    },
});

export default function HorizontalPager(props: IReaderProps) {
    const {
        pages, curPage, settings, setCurPage, prevChapter, nextChapter,
    } = props;

    const classes = useStyles(settings)();

    const selfRef = useRef<HTMLDivElement>(null);
    const pagesRef = useRef<HTMLDivElement[]>([]);

    function nextPage() {
        if (curPage < pages.length - 1) {
            pagesRef.current[curPage + 1]?.scrollIntoView({ inline: 'center' });
            setCurPage((page) => page + 1);
        } else if (settings.loadNextonEnding) {
            nextChapter();
        }
    }

    function prevPage() {
        if (curPage > 0) {
            pagesRef.current[curPage - 1]?.scrollIntoView({ inline: 'center' });
            setCurPage(curPage - 1);
        } else if (curPage === 0) {
            prevChapter();
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
        if (settings.readerType === 'ContinuesHorizontalLTR') {
            if (window.scrollX + window.innerWidth >= document.body.scrollWidth) {
                nextChapter();
            }
        } else {
            if (window.scrollX <= window.innerWidth) {
                nextChapter();
            }
        }
    };

    useEffect(() => {
        pagesRef.current[curPage]?.scrollIntoView({ inline: 'center' });
    }, [settings.readerType]);

    useEffect(() => {
        if (settings.loadNextonEnding) {
            document.addEventListener('scroll', handleLoadNextonEnding);
        }
        selfRef.current?.addEventListener('click', clickControl);

        return () => {
            document.removeEventListener('scroll', handleLoadNextonEnding);
            selfRef.current?.removeEventListener('click', clickControl);
        };
    }, [selfRef, curPage]);

    return (
        <div ref={selfRef} className={classes.reader}>
            {
                pages.map((page) => (
                    <Page
                        key={page.index}
                        index={page.index}
                        src={page.src}
                        onImageLoad={() => {}}
                        setCurPage={setCurPage}
                        settings={settings}
                        ref={(e:HTMLDivElement) => { pagesRef.current[page.index] = e; }}
                    />
                ))
            }
        </div>
    );
}
