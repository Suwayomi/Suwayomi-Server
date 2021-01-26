/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useState, useContext } from 'react';
import { useParams } from 'react-router-dom';
import ChapterCard from '../components/ChapterCard';
import MangaDetails from '../components/MangaDetails';
import NavBarTitle from '../context/NavbarTitle';

export default function Manga() {
    const { id } = useParams<{id: string}>();
    const { setTitle } = useContext(NavBarTitle);

    const [manga, setManga] = useState<IManga>();
    const [chapters, setChapters] = useState<IChapter[]>([]);

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/manga/${id}/`)
            .then((response) => response.json())
            .then((data: IManga) => {
                setManga(data);
                setTitle(data.title);
            });
    }, []);

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/manga/${id}/chapters`)
            .then((response) => response.json())
            .then((data) => setChapters(data));
    }, []);

    const chapterCards = chapters.map((chapter) => (
        <ol style={{ listStyle: 'none', padding: 0 }}>
            <ChapterCard chapter={chapter} />
        </ol>
    ));

    return (
        <>
            <MangaDetails manga={manga} />
            {chapterCards}
        </>
    );
}
