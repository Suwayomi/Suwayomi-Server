/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import ExtensionLangSelect from 'components/manga/ExtensionLangSelect';
import SourceCard from 'components/manga/SourceCard';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';
import { defualtLangs, langCodeToName, langSortCmp } from 'util/language';
import useLocalStorage from 'util/useLocalStorage';

function sourceToLangList(sources: ISource[]) {
    const result: string[] = [];

    sources.forEach((source) => {
        if (result.indexOf(source.lang) === -1) { result.push(source.lang); }
    });

    result.sort(langSortCmp);
    return result;
}

function groupByLang(sources: ISource[]) {
    const result = {} as any;
    sources.forEach((source) => {
        if (result[source.lang] === undefined) { result[source.lang] = [] as ISource[]; }
        result[source.lang].push(source);
    });

    return result;
}

export default function MangaSources() {
    const { setTitle, setAction } = useContext(NavbarContext);

    const [shownLangs, setShownLangs] = useLocalStorage<string[]>('shownSourceLangs', defualtLangs());

    const [sources, setSources] = useState<ISource[]>([]);
    const [fetched, setFetched] = useState<boolean>(false);

    useEffect(() => {
        setTitle('Sources');
        setAction(
            <ExtensionLangSelect
                shownLangs={shownLangs}
                setShownLangs={setShownLangs}
                allLangs={sourceToLangList(sources)}
            />,
        );
    }, [shownLangs, sources]);

    useEffect(() => {
        client.get('/api/v1/source/list')
            .then((response) => response.data)
            .then((data) => { setSources(data); setFetched(true); });
    }, []);

    if (sources.length === 0) {
        if (fetched) return (<h3>No sources found. Install Some Extensions first.</h3>);
        return (<h3>loading...</h3>);
    }
    return (
        <>
            {/* eslint-disable-next-line max-len */}
            {Object.entries(groupByLang(sources)).sort((a, b) => langSortCmp(a[0], b[0])).map(([lang, list]) => (
                shownLangs.indexOf(lang) !== -1 && (
                    <React.Fragment key={lang}>
                        <h1 key={lang} style={{ marginLeft: 25 }}>{langCodeToName(lang)}</h1>
                        {(list as ISource[]).map((source) => (
                            <SourceCard
                                key={source.id}
                                source={source}
                            />
                        ))}
                    </React.Fragment>
                )
            ))}
        </>
    );
}
