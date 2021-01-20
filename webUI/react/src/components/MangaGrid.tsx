import React from 'react';
import MangaCard from './MangaCard';

interface IProps{
    mangas: IManga[]
    message?: string
}

export default function MangaGrid(props: IProps) {
    const { mangas, message } = props;
    let mapped;

    if (mangas.length === 0) {
        mapped = <h3>{message !== undefined ? message : 'loading...'}</h3>;
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
