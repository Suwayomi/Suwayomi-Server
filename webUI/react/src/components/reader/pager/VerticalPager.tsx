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
        pages, settings, setCurPage, curPage, manga, chapter, nextChapter,
    } = props;

    const classes = useStyles();
    const history = useHistory();

    const [initialScroll, setInitialScroll] = useState(-1);
    const initialPageRef = useRef<HTMLDivElement>(null);

    const handleLoadNextonEnding = () => {
        if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight) {
            nextChapter();
        }
    };
    useEffect(() => {
        if (settings.loadNextonEnding) { window.addEventListener('scroll', handleLoadNextonEnding); }

        return () => {
            window.removeEventListener('scroll', handleLoadNextonEnding);
        };
    }, []);

    useEffect(() => {
        if ((chapter as IChapter).lastPageRead > -1) {
            setInitialScroll((chapter as IChapter).lastPageRead);
        }
    }, []);

    useEffect(() => {
        if (initialScroll > -1) {
            initialPageRef.current?.scrollIntoView();
        }
    }, [initialScroll, initialPageRef.current]);

    return (
        <div className={classes.reader}>
            {
                pages.map((page) => (
                    <Page
                        key={page.index}
                        index={page.index}
                        src={page.src}
                        setCurPage={setCurPage}
                        settings={settings}
                        ref={page.index === initialScroll ? initialPageRef : null}
                    />
                ))
            }
        </div>
    );
}
