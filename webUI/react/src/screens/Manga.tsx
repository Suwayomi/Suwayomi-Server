/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useState, useContext } from 'react';
import { makeStyles, Theme, useTheme } from '@material-ui/core/styles';
import { useParams } from 'react-router-dom';
import { Virtuoso } from 'react-virtuoso';
import ChapterCard from '../components/ChapterCard';
import MangaDetails from '../components/MangaDetails';
import NavbarContext from '../context/NavbarContext';
import client from '../util/client';
import LoadingPlaceholder from '../components/LoadingPlaceholder';
import makeToast from '../components/Toast';

const useStyles = makeStyles((theme: Theme) => ({
    root: {
        [theme.breakpoints.up('md')]: {
            display: 'flex',
        },
    },

    chapters: {
        listStyle: 'none',
        padding: 0,
        minHeight: '50vh',
        [theme.breakpoints.up('md')]: {
            width: '50vw',
            height: 'calc(100vh - 64px)',
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
    const theme = useTheme();

    const { setTitle } = useContext(NavbarContext);
    useEffect(() => { setTitle('Manga'); }, []); // delegate setting topbar action to MangaDetails

    const { id } = useParams<{ id: string }>();

    const [manga, setManga] = useState<IManga>();
    const [chapters, setChapters] = useState<IChapter[]>([]);
    const [fetchedChapters, setFetchedChapters] = useState(false);
    const [noChaptersFound, setNoChaptersFound] = useState(false);
    const [chapterUpdateTriggerer, setChapterUpdateTriggerer] = useState(0);

    function triggerChaptersUpdate() {
        setChapterUpdateTriggerer(chapterUpdateTriggerer + 1);
    }

    useEffect(() => {
        if (manga === undefined || !manga.freshData) {
            client.get(`/api/v1/manga/${id}/?onlineFetch=${manga !== undefined}`)
                .then((response) => response.data)
                .then((data: IManga) => {
                    setManga(data);
                    setTitle(data.title);
                });
        }
    }, [manga]);

    useEffect(() => {
        const shouldFetchOnline = fetchedChapters && chapterUpdateTriggerer === 0;
        client.get(`/api/v1/manga/${id}/chapters?onlineFetch=${shouldFetchOnline}`)
            .then((response) => response.data)
            .then((data) => {
                if (data.length === 0 && fetchedChapters) {
                    makeToast('No chapters found', 'warning');
                    setNoChaptersFound(true);
                }
                setChapters(data);
            })
            .then(() => setFetchedChapters(true));
    }, [chapters.length, fetchedChapters, chapterUpdateTriggerer]);

    return (
        <div className={classes.root}>
            <LoadingPlaceholder
                shouldRender={manga !== undefined}
                component={MangaDetails}
                componentProps={{ manga }}
            />

            <LoadingPlaceholder
                shouldRender={chapters.length > 0 || noChaptersFound}
            >
                <Virtuoso
                    style={{ // override Virtuoso default values and set them with class
                        height: 'undefined',
                        overflowY: 'visible',
                    }}
                    className={classes.chapters}
                    totalCount={chapters.length}
                    itemContent={(index:number) => (
                        <ChapterCard
                            chapter={chapters[index]}
                            triggerChaptersUpdate={triggerChaptersUpdate}
                        />
                    )}
                    useWindowScroll={window.innerWidth < 960}
                    overscan={window.innerHeight * 0.5}
                />
            </LoadingPlaceholder>

        </div>
    );
}
