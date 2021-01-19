import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import ChapterCard from '../components/ChapterCard';
import MangaDetails from '../components/MangaDetails';

export default function Manga() {
    const { id } = useParams<{id: string}>();

    const [manga, setManga] = useState<IManga>();
    const [chapters, setChapters] = useState<IChapter[]>([]);

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/manga/${id}/`)
            .then((response) => response.json())
            .then((data) => setManga(data));
    }, []);

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/chapters/${id}/`)
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
