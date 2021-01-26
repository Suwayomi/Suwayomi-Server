/* This Source Code Form is subject to the terms of the Mozilla Public
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

    setDrawerOpen(state: boolean): void
}

export default function TemporaryDrawer({ drawerOpen, setDrawerOpen }: IProps) {
    const classes = useStyles();

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const sideList = (side: 'left') => (
        <div
            className={classes.list}
            role="presentation"
            onClick={() => setDrawerOpen(false)}
            onKeyDown={() => setDrawerOpen(false)}
        >
            <List>
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
                {/* <Link to="/search" style={{ color: 'inherit', textDecoration: 'none' }}>
                    <ListItem button key="Search">
                        <ListItemIcon>
                            <InboxIcon />
                        </ListItemIcon>
                        <ListItemText primary="Global Search" />
                    </ListItem>
                </Link> */}
            </List>
        </div>
    );

    return (
        <div>
            <Drawer
                BackdropProps={{ invisible: true }}
                open={drawerOpen}
                anchor="left"
                onClose={() => setDrawerOpen(false)}
            >
                {sideList('left')}
            </Drawer>
        </div>
    );
}
