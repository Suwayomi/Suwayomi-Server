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

const range = (n:number) => Array.from({ length: n }, (value, key) => key);

export default function Reader() {
    const { setTitle } = useContext(NavBarTitle);

    const [pageCount, setPageCount] = useState<number>(-1);
    const { chapterId, mangaId } = useParams<{chapterId: string, mangaId: string}>();

    useEffect(() => {
        fetch(`http://127.0.0.1:4567/api/v1/manga/${mangaId}/chapter/${chapterId}`)
            .then((response) => response.json())
            .then((data:IChapter) => {
                setTitle(data.name);
                setPageCount(data.pageCount);
            });
    }, []);

    if (pageCount === -1) {
        return (
            <div style={style}>
                <h3>wait</h3>
            </div>
        );
    }

    const mapped = range(pageCount).map((index) => (
        <div style={{ margin: '0 auto' }}>
            <img src={`http://127.0.0.1:4567/api/v1/manga/${mangaId}/chapter/${chapterId}/page/${index}`} alt="f" style={{ maxWidth: '100%' }} />
        </div>
    ));
    return (
        <div style={style}>
            {mapped}
        </div>
    );
}
