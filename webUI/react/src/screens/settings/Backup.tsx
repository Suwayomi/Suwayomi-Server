/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect } from 'react';
import { ListItemIcon } from '@material-ui/core';
import List from '@material-ui/core/List';
import InboxIcon from '@material-ui/icons/Inbox';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import { fromEvent } from 'file-selector';
import ListItemLink from '../../util/ListItemLink';
import NavbarContext from '../../context/NavbarContext';
import client from '../../util/client';

export default function Backup() {
    const { setTitle, setAction } = useContext(NavbarContext);
    useEffect(() => { setTitle('Backup'); setAction(<></>); }, []);

    const { baseURL } = client.defaults;

    const submitBackup = (file: File) => {
        file.text()
            .then(
                (fileContent: string) => {
                    client.post('/api/v1/backup/legacy/import',
                        fileContent, { headers: { 'Content-Type': 'application/json' } });
                },
            );
    };

    const dropHandler = async (e: Event) => {
        e.preventDefault();
        const files = await fromEvent(e);

        submitBackup(files[0] as File);
    };

    const dragOverHandler = (e: Event) => {
        e.preventDefault();
    };

    useEffect(() => {
        document.addEventListener('drop', dropHandler);
        document.addEventListener('dragover', dragOverHandler);

        const input = document.getElementById('backup-file');
        input?.addEventListener('change', async (evt) => {
            const files = await fromEvent(evt);
            submitBackup(files[0] as File);
        });

        return () => {
            document.removeEventListener('drop', dropHandler);
            document.removeEventListener('dragover', dragOverHandler);
        };
    }, []);

    return (
        <List style={{ padding: 0 }}>
            <ListItemLink href={`${baseURL}/api/v1/backup/legacy/export/file`}>
                <ListItemIcon>
                    <InboxIcon />
                </ListItemIcon>
                <ListItemText
                    primary="Create Legacy Backup"
                    secondary="Backup library as a Tachiyomi legacy backup"
                />
            </ListItemLink>
            <ListItem button onClick={() => document.getElementById('backup-file')?.click()}>
                <ListItemIcon>
                    <InboxIcon />
                </ListItemIcon>
                <ListItemText
                    primary="Restore Legacy Backup"
                    secondary="You can also drop the backup file anywhere to restore"
                />
                <input
                    type="file"
                    name="backup.json"
                    id="backup-file"
                    style={{ display: 'none' }}
                />
            </ListItem>
        </List>

    );
}
