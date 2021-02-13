/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import MangaGrid from '../components/MangaGrid';
import NavBarTitle from '../context/NavbarTitle';

export default function MangaList() {
    const { setTitle } = useContext(NavBarTitle);
    const [mangas, setMangas] = useState<IManga[]>([]);
    const [lastPageNum, setLastPageNum] = useState<number>(1);

    useEffect(() => {
        setTitle('Library');
    }, []);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/library')
            .then((response) => response.json())
            .then((data: IManga[]) => {
                setMangas(data);
            });
    }, [lastPageNum]);

    return (
        <MangaGrid
            mangas={mangas}
            hasNextPage={false}
            lastPageNum={lastPageNum}
            setLastPageNum={setLastPageNum}
        />
    );
}
