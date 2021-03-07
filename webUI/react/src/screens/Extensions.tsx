/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import ExtensionCard from '../components/ExtensionCard';
import NavBarTitle from '../context/NavbarTitle';
import client from '../util/client';

export default function Extensions() {
    const { setTitle } = useContext(NavBarTitle);
    setTitle('Extensions');
    const [extensions, setExtensions] = useState<IExtension[]>([]);

    useEffect(() => {
        client.get('/api/v1/extension/list')
            .then((response) => response.data)
            .then((data) => setExtensions(data));
    }, []);

    if (extensions.length === 0) {
        return <h3>loading...</h3>;
    }
    return <>{extensions.map((it) => <ExtensionCard extension={it} />)}</>;
}
