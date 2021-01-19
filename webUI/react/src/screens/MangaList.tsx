import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import MangaCard from '../components/MangaCard';

export default function MangaList(props: { popular: boolean }) {
    const { sourceId } = useParams<{sourceId: string}>();
    let mapped;
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

    if (mangas.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, auto)', gridGap: '1em' }}>
                {mangas.map((it) => (
                    <MangaCard manga={it} />
                ))}
            </div>
        );
    }

    return mapped;
}
