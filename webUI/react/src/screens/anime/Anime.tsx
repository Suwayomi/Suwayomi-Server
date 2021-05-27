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
import EpisodeCard from 'components/anime/EpisodeCard';
import AnimeDetails from 'components/anime/AnimeDetails';
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

export default function Anime() {
    const classes = useStyles();

    const { setTitle } = useContext(NavbarContext);
    useEffect(() => { setTitle('Anime'); }, []); // delegate setting topbar action to MangaDetails

    const { id } = useParams<{ id: string }>();

    const [manga, setManga] = useState<IManga>();
    const [episodes, setEpisodes] = useState<IEpisode[]>([]);
    const [fetchedEpisodes, setFetchedEpisodes] = useState(false);
    const [noEpisodesFound, setNoEpisodesFound] = useState(false);
    const [episodeUpdateTriggerer, setEpisodeUpdateTriggerer] = useState(0);

    function triggerEpisodesUpdate() {
        setEpisodeUpdateTriggerer(episodeUpdateTriggerer + 1);
    }

    useEffect(() => {
        if (manga === undefined || !manga.freshData) {
            client.get(`/api/v1/anime/anime/${id}/?onlineFetch=${manga !== undefined}`)
                .then((response) => response.data)
                .then((data: IManga) => {
                    setManga(data);
                    setTitle(data.title);
                });
        }
    }, [manga]);

    useEffect(() => {
        const shouldFetchOnline = fetchedEpisodes && episodeUpdateTriggerer === 0;
        client.get(`/api/v1/anime/anime/${id}/episodes?onlineFetch=${shouldFetchOnline}`)
            .then((response) => response.data)
            .then((data) => {
                if (data.length === 0 && fetchedEpisodes) {
                    makeToast('No episodes found', 'warning');
                    setNoEpisodesFound(true);
                }
                setEpisodes(data);
            })
            .then(() => setFetchedEpisodes(true));
    }, [episodes.length, fetchedEpisodes, episodeUpdateTriggerer]);

    return (
        <div className={classes.root}>
            <LoadingPlaceholder
                shouldRender={manga !== undefined}
                component={AnimeDetails}
                componentProps={{ manga }}
            />

            <LoadingPlaceholder
                shouldRender={episodes.length > 0 || noEpisodesFound}
            >
                <Virtuoso
                    style={{ // override Virtuoso default values and set them with class
                        height: 'undefined',
                        overflowY: window.innerWidth < 960 ? 'visible' : 'auto',
                    }}
                    className={classes.chapters}
                    totalCount={episodes.length}
                    itemContent={(index:number) => (
                        <EpisodeCard
                            episode={episodes[index]}
                            triggerEpisodesUpdate={triggerEpisodesUpdate}
                        />
                    )}
                    useWindowScroll={window.innerWidth < 960}
                    overscan={window.innerHeight * 0.5}
                />
            </LoadingPlaceholder>

        </div>
    );
}
