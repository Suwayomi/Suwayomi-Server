import React from 'react';
import { useParams } from 'react-router-dom';
import ChapterCard from '../components/ChapterCard';
import MangaDetails from '../components/MangaDetails';

export default function Manga() {
    const { id } = useParams<{id: string}>();

    return (
        <>
            <MangaDetails id={id} />
            <ol style={{ listStyle: 'none', padding: 0 }}>
                <ChapterCard />
            </ol>
        </>
    );
}
