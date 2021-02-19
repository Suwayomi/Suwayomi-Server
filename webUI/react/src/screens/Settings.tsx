/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext } from 'react';
import List from '@material-ui/core/List';
import ListItem, { ListItemProps } from '@material-ui/core/ListItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import InboxIcon from '@material-ui/icons/Inbox';
import Brightness6Icon from '@material-ui/icons/Brightness6';
import { ListItemSecondaryAction, Switch } from '@material-ui/core';
import NavBarTitle from '../context/NavbarTitle';
import DarkTheme from '../context/DarkTheme';

function ListItemLink(props: ListItemProps<'a', { button?: true }>) {
    // eslint-disable-next-line react/jsx-props-no-spreading
    return <ListItem button component="a" {...props} />;
}

export default function Settings() {
    const { setTitle } = useContext(NavBarTitle);
    setTitle('Settings');
    const { darkTheme, setDarkTheme } = useContext(DarkTheme);

    return (
        <div>
            <List component="nav" style={{ padding: 0 }}>
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
            </List>
        </div>
    );
}
