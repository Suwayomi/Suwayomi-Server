/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import ExtensionCard from 'components/anime/ExtensionCard';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';
import useLocalStorage from 'util/useLocalStorage';
import ExtensionLangSelect from 'components/manga/ExtensionLangSelect';
import { defualtLangs, langCodeToName, langSortCmp } from 'util/language';

const allLangs: string[] = [];

function groupExtensions(extensions: IExtension[]) {
    allLangs.length = 0; // empty the array
    const result = { installed: [], 'updates pending': [] } as any;
    extensions.sort((a, b) => ((a.apkName > b.apkName) ? 1 : -1));

    extensions.forEach((extension) => {
        if (result[extension.lang] === undefined) {
            result[extension.lang] = [];
            if (extension.lang !== 'all') { allLangs.push(extension.lang); }
        }
        if (extension.installed) {
            if (extension.hasUpdate) {
                result['updates pending'].push(extension);
            } else {
                result.installed.push(extension);
            }
        } else {
            result[extension.lang].push(extension);
        }
    });

    // put english first for convience
    allLangs.sort(langSortCmp);

    return result;
}

function extensionDefaultLangs() {
    return [...defualtLangs(), 'all'];
}

export default function AnimeExtensions() {
    const { setTitle, setAction } = useContext(NavbarContext);
    const [shownLangs, setShownLangs] = useLocalStorage<string[]>('shownExtensionLangs', extensionDefaultLangs());

    useEffect(() => {
        setTitle('Extensions');
        setAction(
            <ExtensionLangSelect
                shownLangs={shownLangs}
                setShownLangs={setShownLangs}
                allLangs={allLangs}
            />,
        );
    }, [shownLangs]);

    const [extensionsRaw, setExtensionsRaw] = useState<IExtension[]>([]);
    const [extensions, setExtensions] = useState<any>({});

    const [updateTriggerHolder, setUpdateTriggerHolder] = useState(0); // just a hack
    const triggerUpdate = () => setUpdateTriggerHolder(updateTriggerHolder + 1); // just a hack

    useEffect(() => {
        client.get('/api/v1/anime/extension/list')
            .then((response) => response.data)
            .then((data) => setExtensionsRaw(data));
    }, [updateTriggerHolder]);

    useEffect(() => {
        if (extensionsRaw.length > 0) {
            const groupedExtension = groupExtensions(extensionsRaw);
            setExtensions(groupedExtension);
        }
    }, [extensionsRaw]);

    if (Object.entries(extensions).length === 0) {
        return <h3>loading...</h3>;
    }
    const groupsToShow = ['updates pending', 'installed', ...shownLangs];
    return (
        <>
            {
                Object.entries(extensions).map(([lang, list]) => (
                    ((groupsToShow.indexOf(lang) !== -1 && (list as []).length > 0)
                        && (
                            <React.Fragment key={lang}>
                                <h1 key={lang} style={{ marginLeft: 25 }}>
                                    {langCodeToName(lang)}
                                </h1>
                                {(list as IExtension[]).map((it) => (
                                    <ExtensionCard
                                        key={it.apkName}
                                        extension={it}
                                        notifyInstall={() => {
                                            triggerUpdate();
                                        }}
                                    />
                                ))}
                            </React.Fragment>
                        ))
                ))
            }
        </>
    );
}
