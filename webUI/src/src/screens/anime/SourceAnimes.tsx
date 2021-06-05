/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import AnimeGrid from 'components/anime/AnimeGrid';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';

export default function SourceAnimes(props: { popular: boolean }) {
    const { setTitle, setAction } = useContext(NavbarContext);
    useEffect(() => { setTitle('Source'); setAction(<></>); }, []);

    const { sourceId } = useParams<{ sourceId: string }>();
    const [mangas, setMangas] = useState<IMangaCard[]>([]);
    const [hasNextPage, setHasNextPage] = useState<boolean>(false);
    const [lastPageNum, setLastPageNum] = useState<number>(1);

    useEffect(() => {
        client.get(`/api/v1/anime/source/${sourceId}`)
            .then((response) => response.data)
            .then((data: { name: string }) => setTitle(data.name));
    }, []);

    useEffect(() => {
        const sourceType = props.popular ? 'popular' : 'latest';
        client.get(`/api/v1/anime/source/${sourceId}/${sourceType}/${lastPageNum}`)
            .then((response) => response.data)
            .then((data: { mangaList: IManga[], hasNextPage: boolean }) => {
                setMangas([
                    ...mangas,
                    ...data.mangaList.map((it) => ({
                        title: it.title, thumbnailUrl: it.thumbnailUrl, id: it.id,
                    }))]);
                setHasNextPage(data.hasNextPage);
            });
    }, [lastPageNum]);

    return (
        <AnimeGrid
            mangas={mangas}
            hasNextPage={hasNextPage}
            lastPageNum={lastPageNum}
            setLastPageNum={setLastPageNum}
        />
    );
}
