/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import ExtensionCard from '../components/ExtensionCard';
import NavbarContext from '../context/NavbarContext';
import client from '../util/client';
import useLocalStorage from '../util/useLocalStorage';
import ExtensionLangSelect from '../components/ExtensionLangSelect';

const allLangs: string[] = [];

function groupExtensions(extensions: IExtension[]) {
    allLangs.length = 0; // empty the array
    const result = { installed: [] } as any;
    extensions.sort((a, b) => ((a.apkName > b.apkName) ? 1 : -1));

    extensions.forEach((extension) => {
        if (result[extension.lang] === undefined) {
            result[extension.lang] = [];
            allLangs.push(extension.lang);
        }
        if (extension.installed) {
            result.installed.push(extension);
        } else {
            result[extension.lang].push(extension);
        }
    });

    return result;
}

function defualtLangs() {
    return [
        'all',
        'en',
    ];
}

export default function Extensions() {
    const { setTitle, setAction } = useContext(NavbarContext);
    const [shownLangs, setShownLangs] = useLocalStorage<string[]>('shownExtensionLangs', defualtLangs());

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
        client.get('/api/v1/extension/list')
            .then((response) => response.data)
            .then((data) => setExtensionsRaw(data));
    }, [updateTriggerHolder]);

    useEffect(() => {
        if (extensionsRaw.length > 0) {
            const groupedExtension = groupExtensions(extensionsRaw);
            setExtensions(groupedExtension);
        }
    }, [extensionsRaw]);

    if (extensions.length === 0) {
        return <h3>loading...</h3>;
    }
    return (
        <>
            {
                Object.entries(extensions).map(([lang, list]) => (
                    <>
                        {['installed', ...shownLangs].indexOf(lang) !== -1
                        && (
                            <>
                                <h1 key={lang} style={{ marginLeft: 25 }}>{lang}</h1>
                                {(list as IExtension[]).map((it) => (
                                    <ExtensionCard
                                        key={it.apkName}
                                        extension={it}
                                        // eslint-disable-next-line max-len
                                        // eslint-disable-next-line @typescript-eslint/no-unused-vars
                                        notifyInstall={() => {
                                            triggerUpdate();
                                        }}
                                    />
                                ))}
                            </>
                        ) }
                    </>
                ))
            }
        </>
    );
}
