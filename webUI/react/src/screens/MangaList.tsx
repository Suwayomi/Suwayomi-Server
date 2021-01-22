import React, { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import MangaGrid from '../components/MangaGrid';
import NavBarTitle from '../context/NavbarTitle';

export default function MangaList(props: { popular: boolean }) {
    const { sourceId } = useParams<{sourceId: string}>();
    const { setTitle } = useContext(NavBarTitle);
    const [mangas, setMangas] = useState<IManga[]>([]);
    const [lastPageNum] = useState<number>(1);

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/source/${sourceId}`)
            .then((response) => response.json())
            .then((data: { name: string }) => setTitle(data.name));
    }, []);

    useEffect(() => {
        const sourceType = props.popular ? 'popular' : 'latest';
        fetch(`http://127.0.0.1:4567/api/v1/source/${sourceId}/${sourceType}/${lastPageNum}`)
            .then((response) => response.json())
            .then((data: IManga[]) => setMangas(
                data.map((it) => ({ title: it.title, thumbnailUrl: it.thumbnailUrl, id: it.id })),
            ));
    }, []);

    return <MangaGrid mangas={mangas} />;
}
