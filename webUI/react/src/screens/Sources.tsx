/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import SourceCard from '../components/SourceCard';
import NavBarTitle from '../context/NavbarTitle';

export default function Sources() {
    const { setTitle } = useContext(NavBarTitle);
    setTitle('Sources');
    const [sources, setSources] = useState<ISource[]>([]);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/source/list')
            .then((response) => response.json())
            .then((data) => setSources(data));
    }, []);

    if (sources.length === 0) {
        return (<h3>wait</h3>);
    }
    return <>{sources.map((it) => <SourceCard source={it} />)}</>;
}
