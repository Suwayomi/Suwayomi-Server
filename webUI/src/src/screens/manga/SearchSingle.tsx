/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
import { useParams } from 'react-router-dom';
import MangaGrid from 'components/manga/MangaGrid';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';

const useStyles = makeStyles((theme) => ({
    root: {
        TextField: {
            margin: theme.spacing(1),
            width: '25ch',
        },
    },
}));

export default function SearchSingle() {
    const { setTitle, setAction } = useContext(NavbarContext);
    useEffect(() => { setTitle('Search'); setAction(<></>); }, []);

    const { sourceId } = useParams<{ sourceId: string }>();
    const classes = useStyles();
    const [error, setError] = useState<boolean>(false);
    const [mangas, setMangas] = useState<IMangaCard[]>([]);
    const [message, setMessage] = useState<string>('');
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [hasNextPage, setHasNextPage] = useState<boolean>(false);
    const [lastPageNum, setLastPageNum] = useState<number>(1);

    const textInput = React.createRef<HTMLInputElement>();

    useEffect(() => {
        client.get(`/api/v1/source/${sourceId}`)
            .then((response) => response.data)
            .then((data: { name: string }) => setTitle(`Search: ${data.name}`));
    }, []);

    function processInput() {
        if (textInput.current) {
            const { value } = textInput.current;
            if (value === '') {
                setError(true);
                setMessage('Type something to search');
            } else {
                setError(false);
                setSearchTerm(value);
                setMangas([]);
                setMessage('loading...');
            }
        }
    }

    useEffect(() => {
        if (searchTerm.length > 0) {
            client.get(`/api/v1/source/${sourceId}/search/${searchTerm}/${lastPageNum}`)
                .then((response) => response.data)
                .then((data: { mangaList: IManga[], hasNextPage: boolean }) => {
                    setMessage('');
                    if (data.mangaList.length > 0) {
                        setMangas([
                            ...mangas,
                            ...data.mangaList.map((it) => ({
                                title: it.title, thumbnailUrl: it.thumbnailUrl, id: it.id,
                            }))]);
                        setHasNextPage(data.hasNextPage);
                    } else {
                        setMessage('search query returned nothing.');
                    }
                });
        }
    }, [searchTerm]);

    const mangaGrid = (
        <MangaGrid
            mangas={mangas}
            message={message}
            hasNextPage={hasNextPage}
            lastPageNum={lastPageNum}
            setLastPageNum={setLastPageNum}
        />
    );

    return (
        <>
            <div className={classes.root}>
                <TextField
                    inputRef={textInput}
                    error={error}
                    id="standard-basic"
                    label="Search text.."
                    onKeyDown={(e) => e.key === 'Enter' && processInput()}
                />
                <Button variant="contained" color="primary" onClick={() => processInput()}>
                    Search
                </Button>
            </div>
            {mangaGrid}
        </>
    );
}
