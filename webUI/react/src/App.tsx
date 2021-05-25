/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useState } from 'react';
import {
    BrowserRouter as Router, Redirect, Route, Switch,
} from 'react-router-dom';
import { Container } from '@material-ui/core';
import CssBaseline from '@material-ui/core/CssBaseline';
import { createMuiTheme, ThemeProvider } from '@material-ui/core/styles';

import NavBar from './components/navbar/NavBar';
import Sources from './screens/Sources';
import Extensions from './screens/Extensions';
import SourceMangas from './screens/SourceMangas';
import Manga from './screens/Manga';
import Reader from './screens/Reader';
import Search from './screens/SearchSingle';
import NavbarContext from './context/NavbarContext';
import DarkTheme from './context/DarkTheme';
import Library from './screens/Library';
import Settings from './screens/Settings';
import Categories from './screens/settings/Categories';
import Backup from './screens/settings/Backup';
import useLocalStorage from './util/useLocalStorage';

export default function App() {
    const [title, setTitle] = useState<string>('Tachidesk');
    const [action, setAction] = useState<any>(<div />);
    const [override, setOverride] = useState<INavbarOverride>({ status: false, value: <div /> });

    const [darkTheme, setDarkTheme] = useLocalStorage<boolean>('darkTheme', true);

    const navBarContext = {
        title, setTitle, action, setAction, override, setOverride,
    };
    const darkThemeContext = { darkTheme, setDarkTheme };

    const theme = React.useMemo(
        () => createMuiTheme({
            palette: {
                type: darkTheme ? 'dark' : 'light',
            },
            overrides: {
                MuiCssBaseline: {
                    '@global': {
                        '*::-webkit-scrollbar': {
                            width: '10px',
                            background: darkTheme ? '#222' : '#e1e1e1',

                        },
                        '*::-webkit-scrollbar-thumb': {
                            background: darkTheme ? '#111' : '#aaa',
                            borderRadius: '5px',
                        },
                    },
                },
            },
        }),
        [darkTheme],
    );

    return (
        <Router>
            <ThemeProvider theme={theme}>
                <NavbarContext.Provider value={navBarContext}>
                    <CssBaseline />
                    <NavBar />
                    <Container
                        id="appMainContainer"
                        maxWidth={false}
                        disableGutters
                        style={{ paddingTop: '64px' }}
                    >
                        <Switch>
                            <Route path="/sources/:sourceId/search/">
                                <Search />
                            </Route>
                            <Route path="/extensions">
                                <Extensions />
                            </Route>
                            <Route path="/sources/:sourceId/popular/">
                                <SourceMangas popular />
                            </Route>
                            <Route path="/sources/:sourceId/latest/">
                                <SourceMangas popular={false} />
                            </Route>
                            <Route path="/sources">
                                <Sources />
                            </Route>
                            <Route path="/manga/:mangaId/chapter/:chapterNum">
                                <></>
                            </Route>
                            <Route path="/manga/:id">
                                <Manga />
                            </Route>
                            <Route path="/library">
                                <Library />
                            </Route>
                            <Route path="/settings/categories">
                                <Categories />
                            </Route>
                            <Route path="/settings/backup">
                                <Backup />
                            </Route>
                            <Route path="/settings">
                                <DarkTheme.Provider value={darkThemeContext}>
                                    <Settings />
                                </DarkTheme.Provider>
                            </Route>
                            <Route
                                exact
                                path="/"
                                render={() => (
                                    <Redirect to="/library" />
                                )}
                            />
                        </Switch>
                    </Container>
                    <Switch>
                        <Route
                            path="/manga/:mangaId/chapter/:chapterIndex"
                            // passing a key re-mounts the reader when changing chapters
                            render={(props:any) => <Reader key={props.match.params.chapterIndex} />}
                        />
                    </Switch>
                </NavbarContext.Provider>
            </ThemeProvider>
        </Router>
    );
}
