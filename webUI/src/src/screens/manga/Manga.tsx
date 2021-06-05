/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useState, useContext } from 'react';
import { makeStyles, Theme } from '@material-ui/core/styles';
import { useParams } from 'react-router-dom';
import { Virtuoso } from 'react-virtuoso';
import ChapterCard from 'components/manga/ChapterCard';
import MangaDetails from 'components/manga/MangaDetails';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';
import LoadingPlaceholder from 'components/LoadingPlaceholder';
import makeToast from 'components/Toast';

const useStyles = makeStyles((theme: Theme) => ({
    root: {
        [theme.breakpoints.up('md')]: {
            display: 'flex',
        },
    },

    chapters: {
        listStyle: 'none',
        padding: 0,
        minHeight: '200px',
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

const baseWebsocketUrl = JSON.parse(window.localStorage.getItem('serverBaseURL')!).replace('http', 'ws');
const initialQueue = {
    status: 'Stopped',
    queue: [],
} as IQueue;

export default function Manga() {
    const classes = useStyles();

    const { setTitle } = useContext(NavbarContext);
    useEffect(() => { setTitle('Manga'); }, []); // delegate setting topbar action to MangaDetails

    const { id } = useParams<{ id: string }>();

    const [manga, setManga] = useState<IManga>();
    const [chapters, setChapters] = useState<IChapter[]>([]);
    const [fetchedChapters, setFetchedChapters] = useState(false);
    const [noChaptersFound, setNoChaptersFound] = useState(false);
    const [chapterUpdateTriggerer, setChapterUpdateTriggerer] = useState(0);

    const [, setWsClient] = useState<WebSocket>();
    const [{ queue }, setQueueState] = useState<IQueue>(initialQueue);

    function triggerChaptersUpdate() {
        setChapterUpdateTriggerer(chapterUpdateTriggerer + 1);
    }

    useEffect(() => {
        const wsc = new WebSocket(`${baseWebsocketUrl}/api/v1/downloads`);
        wsc.onmessage = (e) => {
            const data = JSON.parse(e.data) as IQueue;
            setQueueState(data);

            let shouldUpdate = false;
            data.queue.forEach((q) => {
                if (q.mangaId === manga?.id && q.state === 'Finished') {
                    shouldUpdate = true;
                }
            });
            if (shouldUpdate) {
                triggerChaptersUpdate();
            }
        };

        setWsClient(wsc);

        return () => wsc.close();
    }, [queue.length]);

    const downloadingStringFor = (chapter: IChapter) => {
        let rtn = '';
        if (chapter.downloaded) {
            rtn = ' • Downloaded';
        }
        queue.forEach((q) => {
            if (chapter.index === q.chapterIndex && chapter.mangaId === q.mangaId) {
                rtn = ` • Downloading (${q.progress * 100}%)`;
            }
        });
        return rtn;
    };

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
                        overflowY: window.innerWidth < 960 ? 'visible' : 'auto',
                    }}
                    className={classes.chapters}
                    totalCount={chapters.length}
                    itemContent={(index:number) => (
                        <ChapterCard
                            chapter={chapters[index]}
                            downloadingString={downloadingStringFor(chapters[index])}
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
