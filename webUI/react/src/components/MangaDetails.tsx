/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
