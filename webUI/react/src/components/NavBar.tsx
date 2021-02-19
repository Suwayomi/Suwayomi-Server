/* eslint-disable @typescript-eslint/no-unused-vars */
// TODO: remove above!
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useContext, useState } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import MoreIcon from '@material-ui/icons/MoreVert';
import AppBar from '@material-ui/core/AppBar';
import Toolbar from '@material-ui/core/Toolbar';
import Typography from '@material-ui/core/Typography';
import IconButton from '@material-ui/core/IconButton';
import MenuIcon from '@material-ui/icons/Menu';
import MenuItem from '@material-ui/core/MenuItem';
import Menu from '@material-ui/core/Menu';

import TemporaryDrawer from './TemporaryDrawer';
import NavBarTitle from '../context/NavbarTitle';
import DarkTheme from '../context/DarkTheme';

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

// const theme = createMuiTheme({
//     overrides: {
//         MuiAppBar: {
//             colorPrimary: { backgroundColor: '#FFC0CB' },
//         },
//     },
//     palette: { type: 'dark' },
// });

export default function NavBar() {
    const classes = useStyles();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
    const { title } = useContext(NavBarTitle);
    const open = Boolean(anchorEl);

    const { darkTheme } = useContext(DarkTheme);

    const handleMenu = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    return (
        <div className={classes.root}>
            <AppBar position="static" color={darkTheme ? 'default' : 'primary'}>
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
                    {/* <IconButton
                        onClick={handleMenu}
                        aria-label="display more actions"
                        edge="end"
                        color="inherit"
                    >
                        <MoreIcon />
                    </IconButton> */}
                    {/* <Menu
                        id="menu-appbar"
                        anchorEl={anchorEl}
                        anchorOrigin={{
                            vertical: 'top',
                            horizontal: 'right',
                        }}
                        keepMounted
                        transformOrigin={{
                            vertical: 'top',
                            horizontal: 'right',
                        }}
                        open={open}
                        onClose={handleClose}
                    >
                        <MenuItem
                            onClick={() => { setDarkTheme(true); handleClose(); }}
                        >
                            Dark Theme

                        </MenuItem>
                        <MenuItem
                            onClick={() => { setDarkTheme(false); handleClose(); }}
                        >
                            Light Theme

                        </MenuItem>
                    </Menu> */}
                </Toolbar>
            </AppBar>
            <TemporaryDrawer drawerOpen={drawerOpen} setDrawerOpen={setDrawerOpen} />
        </div>
    );
}
