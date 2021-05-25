/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import List from '@material-ui/core/List';
import ListAltIcon from '@material-ui/icons/ListAlt';
import BackupIcon from '@material-ui/icons/Backup';
import Brightness6Icon from '@material-ui/icons/Brightness6';
import DnsIcon from '@material-ui/icons/Dns';
import EditIcon from '@material-ui/icons/Edit';
import InfoIcon from '@material-ui/icons/Info';
import ListItem from '@material-ui/core/ListItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import ListItemSecondaryAction from '@material-ui/core/ListItemSecondaryAction';
import Switch from '@material-ui/core/Switch';
import Button from '@material-ui/core/Button';
import IconButton from '@material-ui/core/IconButton';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import TextField from '@material-ui/core/TextField';
import NavbarContext from '../context/NavbarContext';
import DarkTheme from '../context/DarkTheme';
import useLocalStorage from '../util/useLocalStorage';
import ListItemLink from '../util/ListItemLink';

export default function Settings() {
    const { setTitle, setAction } = useContext(NavbarContext);
    useEffect(() => { setTitle('Settings'); setAction(<></>); }, []);

    const { darkTheme, setDarkTheme } = useContext(DarkTheme);
    const [serverAddress, setServerAddress] = useLocalStorage<String>('serverBaseURL', '');
    const [dialogOpen, setDialogOpen] = useState(false);
    const [dialogValue, setDialogValue] = useState(serverAddress);

    const handleDialogOpen = () => {
        setDialogValue(serverAddress);
        setDialogOpen(true);
    };

    const handleDialogCancel = () => {
        setDialogOpen(false);
    };

    const handleDialogSubmit = () => {
        setDialogOpen(false);
        setServerAddress(dialogValue);
    };

    return (
        <>
            <List style={{ padding: 0 }}>
                <ListItemLink href="/settings/categories">
                    <ListItemIcon>
                        <ListAltIcon />
                    </ListItemIcon>
                    <ListItemText primary="Categories" />
                </ListItemLink>
                <ListItemLink href="/settings/backup">
                    <ListItemIcon>
                        <BackupIcon />
                    </ListItemIcon>
                    <ListItemText primary="Backup" />
                </ListItemLink>
                <ListItem>
                    <ListItemIcon>
                        <Brightness6Icon />
                    </ListItemIcon>
                    <ListItemText primary="Dark Theme" />
                    <ListItemSecondaryAction>
                        <Switch
                            edge="end"
                            checked={darkTheme}
                            onChange={() => setDarkTheme(!darkTheme)}
                        />
                    </ListItemSecondaryAction>
                </ListItem>
                <ListItem>
                    <ListItemIcon>
                        <DnsIcon />
                    </ListItemIcon>
                    <ListItemText primary="Server Address" secondary={serverAddress} />
                    <ListItemSecondaryAction>
                        <IconButton
                            onClick={() => {
                                handleDialogOpen();
                            }}
                        >
                            <EditIcon />
                        </IconButton>
                    </ListItemSecondaryAction>
                </ListItem>
                <ListItemLink href="/settings/about">
                    <ListItemIcon>
                        <InfoIcon />
                    </ListItemIcon>
                    <ListItemText primary="About" />
                </ListItemLink>
            </List>

            <Dialog open={dialogOpen} onClose={handleDialogCancel}>
                <DialogContent>
                    <DialogContentText>
                        Enter new category name.
                    </DialogContentText>
                    <TextField
                        autoFocus
                        margin="dense"
                        id="name"
                        label="Category Name"
                        type="text"
                        fullWidth
                        value={dialogValue}
                        onChange={(e) => setDialogValue(e.target.value)}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleDialogCancel} color="primary">
                        Cancel
                    </Button>
                    <Button onClick={handleDialogSubmit} color="primary">
                        Set
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
}
