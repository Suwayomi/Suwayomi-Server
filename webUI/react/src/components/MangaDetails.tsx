import React from 'react';

interface IProps{
    manga: IManga | undefined
}

export default function MangaDetails(props: IProps) {
    const { manga } = props;

    return (
        <>
            <h1>
                {manga && manga.title}
            </h1>
        </>
    );
}
