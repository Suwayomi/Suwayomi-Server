/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import NavbarContext from '../context/NavbarContext';
import client from '../util/client';
import useLocalStorage from '../util/useLocalStorage';

const style = {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    margin: '0 auto',
    backgroundColor: '#343a40',
} as React.CSSProperties;

const range = (n:number) => Array.from({ length: n }, (value, key) => key);

export default function Reader() {
    const { setTitle, setAction } = useContext(NavbarContext);
    useEffect(() => { setTitle('Reader'); setAction(<></>); }, []);

    const [serverAddress] = useLocalStorage<String>('serverBaseURL', '');

    const [pageCount, setPageCount] = useState<number>(-1);
    const { chapterId, mangaId } = useParams<{chapterId: string, mangaId: string}>();

    useEffect(() => {
        client.get(`/api/v1/manga/${mangaId}/chapter/${chapterId}`)
            .then((response) => response.data)
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
            <img src={`${serverAddress}/api/v1/manga/${mangaId}/chapter/${chapterId}/page/${index}`} alt="F" style={{ maxWidth: '100%' }} />
        </div>
    ));
    return (
        <div style={style}>
            {mapped}
        </div>
    );
}
