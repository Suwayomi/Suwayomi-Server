import React, { useEffect, useState } from 'react';

interface IProps{
    id: string
}

export default function MangaDetails(props: IProps) {
    const { id } = props;
    const [manga, setManga] = useState<IManga>();

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/manga/${id}/`)
            .then((response) => response.json())
            .then((data) => setManga(data));
    }, []);

    return (
        <>
            <h1>
                {manga && manga.title}
            </h1>
        </>
    );
}
