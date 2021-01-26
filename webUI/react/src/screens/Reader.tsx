/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import NavBarTitle from '../context/NavbarTitle';

const style = {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    margin: '0 auto',
    backgroundColor: '#343a40',
} as React.CSSProperties;

interface IPage {
    index: number
    imageUrl: string
}

interface IData {
    first: IChapter
    second: IPage[]
}

export default function Reader() {
    const { setTitle } = useContext(NavBarTitle);

    const [pages, setPages] = useState<IPage[]>([]);
    const { chapterId, mangaId } = useParams<{chapterId: string, mangaId: string}>();

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/manga/${mangaId}/chapter/${chapterId}`)
            .then((response) => response.json())
            .then((data:IData) => {
                setTitle(data.first.name);
                setPages(data.second);
            });
    }, []);

    pages.sort((a, b) => (a.index - b.index));

    let mapped;
    if (pages.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = pages.map(({ imageUrl }) => (
            <div style={{ margin: '0 auto' }}>
                <img src={imageUrl} alt="f" style={{ maxWidth: '100%' }} />
            </div>
        ));
    }

    return (
        <div style={style}>
            {mapped}
        </div>
    );
}
