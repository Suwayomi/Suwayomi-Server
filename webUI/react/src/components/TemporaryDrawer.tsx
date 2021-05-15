/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Drawer from '@material-ui/core/Drawer';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import InboxIcon from '@material-ui/icons/MoveToInbox';
import { Link } from 'react-router-dom';

const useStyles = makeStyles({
    list: {
        width: 250,
    },
});

interface IProps {
    drawerOpen: boolean

    setDrawerOpen: React.Dispatch<React.SetStateAction<boolean>>
}

export default function TemporaryDrawer({ drawerOpen, setDrawerOpen }: IProps) {
    const classes = useStyles();

    return (
        <div>
            <Drawer
                open={drawerOpen}
                anchor="left"
                onClose={() => setDrawerOpen(false)}
            >
                <div
                    className={classes.list}
                    role="presentation"
                    onClick={() => setDrawerOpen(false)}
                    onKeyDown={() => setDrawerOpen(false)}
                >
                    <List>
                        <Link to="/library" style={{ color: 'inherit', textDecoration: 'none' }}>
                            <ListItem button key="Library">
                                <ListItemIcon>
                                    <InboxIcon />
                                </ListItemIcon>
                                <ListItemText primary="Library" />
                            </ListItem>
                        </Link>
                        <Link to="/extensions" style={{ color: 'inherit', textDecoration: 'none' }}>
                            <ListItem button key="Extensions">
                                <ListItemIcon>
                                    <InboxIcon />
                                </ListItemIcon>
                                <ListItemText primary="Extensions" />
                            </ListItem>
                        </Link>
                        <Link to="/sources" style={{ color: 'inherit', textDecoration: 'none' }}>
                            <ListItem button key="Sources">
                                <ListItemIcon>
                                    <InboxIcon />
                                </ListItemIcon>
                                <ListItemText primary="Sources" />
                            </ListItem>
                        </Link>
                        <Link to="/settings" style={{ color: 'inherit', textDecoration: 'none' }}>
                            <ListItem button key="settings">
                                <ListItemIcon>
                                    <InboxIcon />
                                </ListItemIcon>
                                <ListItemText primary="Settings" />
                            </ListItem>
                        </Link>
                    </List>
                </div>
            </Drawer>
        </div>
    );
}
