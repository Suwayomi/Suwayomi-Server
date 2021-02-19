/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import MangaGrid from '../components/MangaGrid';
import NavBarTitle from '../context/NavbarTitle';

export default function SourceMangas(props: { popular: boolean }) {
    const { sourceId } = useParams<{sourceId: string}>();
    const { setTitle } = useContext(NavBarTitle);
    const [mangas, setMangas] = useState<IManga[]>([]);
    const [hasNextPage, setHasNextPage] = useState<boolean>(false);
    const [lastPageNum, setLastPageNum] = useState<number>(1);

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/source/${sourceId}`)
            .then((response) => response.json())
            .then((data: { name: string }) => setTitle(data.name));
    }, []);

    useEffect(() => {
        const sourceType = props.popular ? 'popular' : 'latest';
        fetch(`http://127.0.0.1:4567/api/v1/source/${sourceId}/${sourceType}/${lastPageNum}`)
            .then((response) => response.json())
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
        <MangaGrid
            mangas={mangas}
            hasNextPage={hasNextPage}
            lastPageNum={lastPageNum}
            setLastPageNum={setLastPageNum}
        />
    );
}
