/* eslint-disable @typescript-eslint/no-unused-vars */
/* This Source Code Form is subject to the terms of the Mozilla Public
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

const useStyles = makeStyles((theme: Theme) => ({
    root: {
        [theme.breakpoints.up('md')]: {
            display: 'flex',
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
        <ol style={{ listStyle: 'none', padding: 0, width: '100%' }}>
            {chapters.length === 0
            && (
                <div className={classes.loading}>
                    <CircularProgress thickness={5} />
                </div>
            ) }
            {chapters.map((chapter) => (<ChapterCard chapter={chapter} />))}
        </ol>
    );

    return (
        <div className={classes.root}>
            {manga && <MangaDetails manga={manga} />}
            {chapterCards}
        </div>
    );
}
