/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useRef } from 'react';
import Grid from '@material-ui/core/Grid';
import MangaCard from './MangaCard';

interface IProps{
    mangas: IMangaCard[]
    message?: string
    hasNextPage: boolean
    lastPageNum: number
    setLastPageNum: (lastPageNum: number) => void
}

export default function MangaGrid(props: IProps) {
    const {
        mangas, message, hasNextPage, lastPageNum, setLastPageNum,
    } = props;
    let mapped;
    const lastManga = useRef<HTMLInputElement>();

    const scrollHandler = () => {
        if (lastManga.current) {
            const rect = lastManga.current.getBoundingClientRect();
            if (((rect.y + rect.height) / window.innerHeight < 2) && hasNextPage) {
                setLastPageNum(lastPageNum + 1);
            }
        }
    };
    useEffect(() => {
        window.addEventListener('scroll', scrollHandler, true);
        return () => {
            window.removeEventListener('scroll', scrollHandler, true);
        };
    }, [hasNextPage, mangas]);

    if (mangas.length === 0) {
        mapped = <h3>{message}</h3>;
    } else {
        mapped = mangas.map((it, idx) => {
            if (idx === mangas.length - 1) {
                return <MangaCard manga={it} ref={lastManga} />;
            }
            return <MangaCard manga={it} />;
        });
    }

    return (
        <Grid container spacing={1} style={{ margin: 0, width: '100%', padding: '5px' }}>
            {mapped}
        </Grid>
    );
}

MangaGrid.defaultProps = {
    message: 'loading...',
};
