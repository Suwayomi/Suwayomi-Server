/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useState } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import Button from '@material-ui/core/Button';
import Avatar from '@material-ui/core/Avatar';
import Typography from '@material-ui/core/Typography';
import client from 'util/client';
import useLocalStorage from 'util/useLocalStorage';

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
    extension: IExtension
    notifyInstall: () => void
}

export default function ExtensionCard(props: IProps) {
    const {
        extension: {
            name, lang, versionName, installed, hasUpdate, obsolete, pkgName, iconUrl,
        },
        notifyInstall,
    } = props;
    const [installedState, setInstalledState] = useState<string>(
        () => {
            if (obsolete) { return 'obsolete'; }
            if (hasUpdate) { return 'update'; }
            return (installed ? 'uninstall' : 'install');
        },
    );

    const [serverAddress] = useLocalStorage<String>('serverBaseURL', '');

    const classes = useStyles();
    const langPress = lang === 'all' ? 'All' : lang.toUpperCase();

    function install() {
        setInstalledState('installing');
        client.get(`/api/v1/extension/install/${pkgName}`)
            .then(() => {
                setInstalledState('uninstall');
                notifyInstall();
            });
    }

    function update() {
        setInstalledState('updating');
        client.get(`/api/v1/extension/update/${pkgName}`)
            .then(() => {
                setInstalledState('uninstall');
                notifyInstall();
            });
    }

    function uninstall() {
        setInstalledState('uninstalling');
        client.get(`/api/v1/extension/uninstall/${pkgName}`)
            .then(() => {
                // setInstalledState('install');
                notifyInstall();
            });
    }

    function handleButtonClick() {
        switch (installedState) {
            case 'install':
                install();
                break;
            case 'update':
                update();
                break;
            case 'obsolete':
                uninstall();
                setTimeout(() => window.location.reload(), 3000);
                break;
            case 'uninstall':
                uninstall();
                break;
            default:
                break;
        }
    }

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
                            {langPress}
                            {' '}
                            {versionName}
                        </Typography>
                    </div>
                </div>

                <Button
                    variant="outlined"
                    style={{ color: installedState === 'obsolete' ? 'red' : 'inherit' }}
                    onClick={() => handleButtonClick()}
                >
                    {installedState}

                </Button>
            </CardContent>
        </Card>
    );
}
