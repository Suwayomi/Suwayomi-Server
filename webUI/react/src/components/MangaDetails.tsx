/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { Button } from '@material-ui/core';
import React, { useState } from 'react';

interface IProps{
    manga: IManga
}

export default function MangaDetails(props: IProps) {
    const { manga } = props;
    const [inLibrary, setInLibrary] = useState<string>(
        manga.inLibrary ? 'In Library' : 'Not In Library',
    );

    function addToLibrary() {
        setInLibrary('adding');
        fetch(`http://127.0.0.1:4567/api/v1/manga/${manga.id}/library/`).then(() => {
            setInLibrary('In Library');
        });
    }

    function removeFromLibrary() {
        setInLibrary('removing');
        fetch(`http://127.0.0.1:4567/api/v1/manga/${manga.id}/library/`, { method: 'DELETE', mode: 'cors' }).then(() => {
            setInLibrary('Not In Library');
        });
    }

    function handleButtonClick() {
        if (inLibrary === 'Not In Library') {
            addToLibrary();
        } else {
            removeFromLibrary();
        }
    }

    return (
        <>
            <h1>
                {manga && manga.title}
            </h1>
            <div style={{ display: 'flex', flexDirection: 'row-reverse' }}>
                <Button variant="outlined" onClick={() => handleButtonClick()}>{inLibrary}</Button>
            </div>
        </>
    );
}
