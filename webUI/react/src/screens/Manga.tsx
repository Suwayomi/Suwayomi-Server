/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useState, useContext } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import { useParams } from 'react-router-dom';
import CircularProgress from '@material-ui/core/CircularProgress';
import ChapterCard from '../components/ChapterCard';
import MangaDetails from '../components/MangaDetails';
import NavbarContext from '../context/NavbarContext';
import client from '../util/client';
import LoadingPlaceholder from '../components/LoadingPlaceholder';

const useStyles = makeStyles((theme: Theme) => ({
    root: {
        [theme.breakpoints.up('md')]: {
            display: 'flex',
        },
    },

    chapters: {
        listStyle: 'none',
        padding: 0,
        [theme.breakpoints.up('md')]: {
            width: '50vw',
            height: 'calc(100vh - 64px)',
            overflowY: 'auto',
            margin: 0,
        },
    },

    loading: {
        margin: '10px 0',
        display: 'flex',
        justifyContent: 'center',
    },
}));

export default function Manga() {
    const classes = useStyles();

    const { setTitle } = useContext(NavbarContext);
    useEffect(() => { setTitle('Manga'); }, []); // delegate setting topbar action to MangaDetails

    const { id } = useParams<{id: string}>();

    const [manga, setManga] = useState<IManga>();
    const [chapters, setChapters] = useState<IChapter[]>([]);

    useEffect(() => {
        client.get(`/api/v1/manga/${id}/`)
            .then((response) => response.data)
            .then((data: IManga) => {
                setManga(data);
                setTitle(data.title);
            });
    }, []);

    useEffect(() => {
        client.get(`/api/v1/manga/${id}/chapters`)
            .then((response) => response.data)
            .then((data) => setChapters(data));
    }, []);

    const chapterCards = (
        <LoadingPlaceholder
            shouldRender={chapters.length > 0}
        >
            <ol className={classes.chapters}>
                {chapters.map((chapter) => (<ChapterCard chapter={chapter} />))}
            </ol>
        </LoadingPlaceholder>

    );

    return (
        <div className={classes.root}>
            <LoadingPlaceholder
                shouldRender={manga !== undefined}
                component={MangaDetails}
                componentProps={{ manga }}
            />
            {chapterCards}
        </div>
    );
}
