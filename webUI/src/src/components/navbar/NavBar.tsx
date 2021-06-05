/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useState } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import AppBar from '@material-ui/core/AppBar';
import Toolbar from '@material-ui/core/Toolbar';
import Typography from '@material-ui/core/Typography';
import IconButton from '@material-ui/core/IconButton';
import MenuIcon from '@material-ui/icons/Menu';
import NavBarContext from '../../context/NavbarContext';
import DarkTheme from '../../context/DarkTheme';
import TemporaryDrawer from '../TemporaryDrawer';

const useStyles = makeStyles((theme) => ({
    root: {
        flexGrow: 1,
    },
    menuButton: {
        marginRight: theme.spacing(2),
    },
    title: {
        flexGrow: 1,
    },
}));

export default function NavBar() {
    const classes = useStyles();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const { title, action, override } = useContext(NavBarContext);

    const { darkTheme } = useContext(DarkTheme);

    return (
        <>
            {override.status && override.value}
            {!override.status
        && (
            <div className={classes.root}>
                <AppBar position="fixed" color={darkTheme ? 'default' : 'primary'}>
                    <Toolbar>
                        <IconButton
                            edge="start"
                            className={classes.menuButton}
                            color="inherit"
                            aria-label="menu"
                            disableRipple
                            onClick={() => setDrawerOpen(true)}
                        >
                            <MenuIcon />
                        </IconButton>
                        <Typography variant="h6" className={classes.title}>
                            {title}
                        </Typography>
                        {action}
                    </Toolbar>
                </AppBar>
                <TemporaryDrawer drawerOpen={drawerOpen} setDrawerOpen={setDrawerOpen} />
            </div>
        )}
        </>
    );
}
