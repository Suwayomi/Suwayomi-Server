/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import Button from '@material-ui/core/Button';
import Avatar from '@material-ui/core/Avatar';
import Typography from '@material-ui/core/Typography';
import useLocalStorage from 'util/useLocalStorage';
import { langCodeToName } from 'util/language';

const useStyles = makeStyles((theme) => ({
    root: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: 16,
    },
    bullet: {
        display: 'inline-block',
        margin: '0 2px',
        transform: 'scale(0.8)',
    },
    title: {
        fontSize: 14,
    },
    pos: {
        marginBottom: 12,
    },
    icon: {
        width: theme.spacing(7),
        height: theme.spacing(7),
        flex: '0 0 auto',
        marginRight: 16,
    },
}));

interface IProps {
    source: ISource
}

export default function SourceCard(props: IProps) {
    const {
        source: {
            id, name, lang, iconUrl, supportsLatest, isConfigurable,
        },
    } = props;

    const [serverAddress] = useLocalStorage<String>('serverBaseURL', '');

    const classes = useStyles();

    return (
        <Card>
            <CardContent className={classes.root}>
                <div style={{ display: 'flex' }}>
                    <Avatar
                        variant="rounded"
                        className={classes.icon}
                        alt={name}
                        src={serverAddress + iconUrl}
                    />
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                        <Typography variant="h5" component="h2">
                            {name}
                        </Typography>
                        <Typography variant="caption" display="block" gutterBottom>
                            {langCodeToName(lang)}
                        </Typography>
                    </div>
                </div>
                <div style={{ display: 'flex' }}>
                    {isConfigurable && <Button variant="outlined" style={{ marginLeft: 20 }} onClick={() => { window.location.href = `/sources/${id}/configure/`; }}>Configure</Button>}
                    <Button variant="outlined" style={{ marginLeft: 20 }} onClick={() => { window.location.href = `/sources/${id}/search/`; }}>Search</Button>
                    {supportsLatest && <Button variant="outlined" style={{ marginLeft: 20 }} onClick={() => { window.location.href = `/sources/${id}/latest/`; }}>Latest</Button>}
                    <Button variant="outlined" style={{ marginLeft: 20 }} onClick={() => { window.location.href = `/sources/${id}/popular/`; }}>Browse</Button>
                </div>
            </CardContent>
        </Card>
    );
}
