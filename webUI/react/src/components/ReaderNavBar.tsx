/* eslint-disable @typescript-eslint/no-unused-vars */
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import IconButton from '@material-ui/core/IconButton';
import CloseIcon from '@material-ui/icons/Close';
import KeyboardArrowLeftIcon from '@material-ui/icons/KeyboardArrowLeft';
import KeyboardArrowRightIcon from '@material-ui/icons/KeyboardArrowRight';
import { makeStyles, Theme, useTheme } from '@material-ui/core/styles';
import React, { useContext, useEffect, useState } from 'react';
import Typography from '@material-ui/core/Typography';
import { useHistory } from 'react-router-dom';
import Slide from '@material-ui/core/Slide';
import Fade from '@material-ui/core/Fade';
import Zoom from '@material-ui/core/Zoom';
import { Switch } from '@material-ui/core';
import NavBarContext from '../context/NavbarContext';
import DarkTheme from '../context/DarkTheme';

const useStyles = (settings: IReaderSettings) => makeStyles((theme: Theme) => ({
    // main container and root div need to change classes...
    AppMainContainer: {
        display: 'none',
    },
    AppRootElment: {
        display: 'flex',
    },

    root: {
        position: settings.staticNav ? 'sticky' : 'fixed',
        top: 0,
        left: 0,
        minWidth: '300px',
        height: '100vh',
        overflowY: 'auto',
        backgroundColor: '#0a0b0b',

        '& header': {
            backgroundColor: '#363b3d',
            display: 'flex',
            alignItems: 'center',
            minHeight: '64px',
            paddingLeft: '24px',
            paddingRight: '24px',

            transition: 'left 2s ease',

            '& button': {
                flexGrow: 0,
                flexShrink: 0,
            },

            '& button:nth-child(1)': {
                marginRight: '16px',
            },

            '& button:nth-child(3)': {
                marginRight: '-12px',
            },

            '& h1': {
                fontSize: '1.25rem',
                flexGrow: 1,
            },
        },
    },

    openDrawerButton: {
        position: 'fixed',
        top: 0 + 20,
        left: 10 + 20,
        height: '40px',
        width: '40px',
        borderRadius: 5,
        backgroundColor: 'black',

        '&:hover': {
            backgroundColor: 'black',
        },
    },
}));

export interface IReaderSettings{
    staticNav: boolean
    showPageNumber: boolean
}

export const defaultReaderSettings = () => ({
    staticNav: false,
    showPageNumber: true,
} as IReaderSettings);

interface IProps {
    settings: IReaderSettings
    setSettings: React.Dispatch<React.SetStateAction<IReaderSettings>>
    manga: IMangaCard | IManga
}

export default function ReaderNavBar(props: IProps) {
    const { title } = useContext(NavBarContext);
    const { darkTheme } = useContext(DarkTheme);

    const history = useHistory();

    const { settings, setSettings, manga } = props;

    const [drawerOpen, setDrawerOpen] = useState(false || settings.staticNav);
    const [hideOpenButton, setHideOpenButton] = useState(false);
    const [prevScrollPos, setPrevScrollPos] = useState(0);

    const theme = useTheme();
    const classes = useStyles(settings)();

    const setSettingValue = (key: string, value: any) => setSettings({ ...settings, [key]: value });

    const handleScroll = () => {
        const currentScrollPos = window.pageYOffset;

        if (Math.abs(currentScrollPos - prevScrollPos) > 20) {
            setHideOpenButton(currentScrollPos > prevScrollPos);
            setPrevScrollPos(currentScrollPos);
        }
    };

    useEffect(() => {
        window.addEventListener('scroll', handleScroll);

        return () => {
            window.removeEventListener('scroll', handleScroll);
        };
    }, [handleScroll]);// handleScroll changes on every render

    useEffect(() => {
        window.addEventListener('scroll', handleScroll);

        const rootEl = document.querySelector('#root')!;
        const mainContainer = document.querySelector('#appMainContainer')!;

        rootEl.classList.add(classes.AppRootElment);
        mainContainer.classList.add(classes.AppMainContainer);

        return () => {
            rootEl.classList.remove(classes.AppRootElment);
            mainContainer.classList.remove(classes.AppMainContainer);
        };
    }, [handleScroll]);// handleScroll changes on every render

    return (
        <>
            <Slide direction="right" in={drawerOpen} timeout={200} appear={false}>
                <div className={classes.root}>
                    <header>
                        <IconButton
                            edge="start"
                            color="inherit"
                            aria-label="menu"
                            disableRipple
                            onClick={() => history.push(`/manga/${manga.id}`)}
                        >
                            <CloseIcon />
                        </IconButton>
                        <Typography variant="h1">
                            {title}
                        </Typography>
                        {!settings.staticNav
                        && (
                            <IconButton
                                edge="start"
                                color="inherit"
                                aria-label="menu"
                                disableRipple
                                onClick={() => setDrawerOpen(false)}
                            >
                                <KeyboardArrowLeftIcon />
                            </IconButton>
                        ) }
                    </header>
                    <h3>Static Navigation</h3>
                    <Switch
                        checked={settings.staticNav}
                        onChange={(e) => setSettingValue('staticNav', e.target.checked)}
                    />
                    <h3>Show page number</h3>
                    <Switch
                        checked={settings.showPageNumber}
                        onChange={(e) => setSettingValue('showPageNumber', e.target.checked)}
                    />
                </div>
            </Slide>
            <Zoom in={!drawerOpen}>
                <Fade in={!hideOpenButton}>
                    <IconButton
                        className={classes.openDrawerButton}
                        edge="start"
                        color="inherit"
                        aria-label="menu"
                        disableRipple
                        disableFocusRipple
                        onClick={() => setDrawerOpen(true)}
                    >
                        <KeyboardArrowRightIcon />
                    </IconButton>
                </Fade>
            </Zoom>
        </>
    );
}
