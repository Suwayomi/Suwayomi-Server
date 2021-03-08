/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useState, useContext } from 'react';
import { useParams } from 'react-router-dom';
import ChapterCard from '../components/ChapterCard';
import MangaDetails from '../components/MangaDetails';
import NavbarContext from '../context/NavbarContext';
import client from '../util/client';

export default function Manga() {
    const { id } = useParams<{id: string}>();
    const { setTitle } = useContext(NavbarContext);

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

    const chapterCards = chapters.map((chapter) => (
        <ol style={{ listStyle: 'none', padding: 0 }}>
            <ChapterCard chapter={chapter} />
        </ol>
    ));

    return (
        <>
            {manga && <MangaDetails manga={manga} />}
            {chapterCards}
        </>
    );
}
