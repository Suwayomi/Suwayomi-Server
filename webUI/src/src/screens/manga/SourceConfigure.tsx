/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import NavbarContext from 'context/NavbarContext';
import { useParams } from 'react-router-dom';
import client from 'util/client';
import CheckBoxPreference from 'components/manga/sourceConfiguration/CheckBoxPreference';
import List from '@material-ui/core/List';

function getPrefComponent(type: string) {
    switch (type) {
        case 'CheckBoxPreference':
            return CheckBoxPreference;
        default:
            return CheckBoxPreference;
    }
}

export default function SourceConfigure() {
    const [sourcePreferences, setSourcePreferences] = useState<SourcePreferences[]>([]);
    const { setTitle, setAction } = useContext(NavbarContext);

    useEffect(() => { setTitle('Source Configuration'); setAction(<></>); }, []);

    const { sourceId } = useParams<{ sourceId: string }>();

    useEffect(() => {
        client.get(`/api/v1/source/${sourceId}/preferences`)
            .then((response) => response.data)
            .then((data) => setSourcePreferences(data));
    }, []);

    console.log(sourcePreferences);
    return (
        <>
            <List style={{ padding: 0 }}>

                {sourcePreferences.map(
                    (it) => React.createElement(getPrefComponent(it.type), it.props),
                )}
            </List>
        </>
    );
}
