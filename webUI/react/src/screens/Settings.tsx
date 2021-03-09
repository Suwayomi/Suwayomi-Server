/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useEffect, useState } from 'react';
import List from '@material-ui/core/List';
import InboxIcon from '@material-ui/icons/Inbox';
import Brightness6Icon from '@material-ui/icons/Brightness6';
import DnsIcon from '@material-ui/icons/Dns';
import EditIcon from '@material-ui/icons/Edit';
import {
    Button, Dialog, DialogActions, DialogContent,
    DialogContentText, IconButton, ListItemSecondaryAction, Switch, TextField,
    ListItemIcon, ListItemText,
} from '@material-ui/core';
import ListItem, { ListItemProps } from '@material-ui/core/ListItem';
import NavbarContext from '../context/NavbarContext';
import DarkTheme from '../context/DarkTheme';
import useLocalStorage from '../util/useLocalStorage';

function ListItemLink(props: ListItemProps<'a', { button?: true }>) {
    // eslint-disable-next-line react/jsx-props-no-spreading
    return <ListItem button component="a" {...props} />;
}

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
                        <InboxIcon />
                    </ListItemIcon>
                    <ListItemText primary="Categories" />
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
