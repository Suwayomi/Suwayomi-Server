import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import MangaGrid from '../components/MangaGrid';

export default function MangaList(props: { popular: boolean }) {
    const { sourceId } = useParams<{sourceId: string}>();
    const [mangas, setMangas] = useState<IManga[]>([]);
    const [lastPageNum] = useState<number>(1);

    useEffect(() => {
        const sourceType = props.popular ? 'popular' : 'latest';
        fetch(`http://127.0.0.1:4567/api/v1/source/${sourceId}/${sourceType}/${lastPageNum}`)
            .then((response) => response.json())
            .then((data: { title: string, thumbnail_url: string, id:number }[]) => setMangas(
                data.map((it) => ({ title: it.title, thumbnailUrl: it.thumbnail_url, id: it.id })),
            ));
    }, []);

    return <MangaGrid mangas={mangas} />;
}
