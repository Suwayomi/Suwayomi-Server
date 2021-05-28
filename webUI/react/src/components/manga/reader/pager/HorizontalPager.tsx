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
        pages, curPage, settings, setCurPage,
    } = props;

    const classes = useStyles(settings)();

    const selfRef = useRef<HTMLDivElement>(null);
    const pagesRef = useRef<HTMLDivElement[]>([]);

    useEffect(() => {
        pagesRef.current[curPage]?.scrollIntoView({ inline: 'center' });
    }, [settings.readerType]);

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
