import React, { useState } from 'react';
import {
    BrowserRouter as Router, Route, Switch,
} from 'react-router-dom';
import { Container } from '@material-ui/core';
import CssBaseline from '@material-ui/core/CssBaseline';
import { createMuiTheme, ThemeProvider } from '@material-ui/core/styles';

import NavBar from './components/NavBar';
import Home from './screens/Home';
import Sources from './screens/Sources';
import Extensions from './screens/Extensions';
import MangaList from './screens/MangaList';
import Manga from './screens/Manga';
import Reader from './screens/Reader';
import Search from './screens/SearchSingle';
import NavBarTitle from './context/NavbarTitle';
import DarkTheme from './context/DarkTheme';

export default function App() {
    const [title, setTitle] = useState<string>('Tachidesk');
    const [darkTheme, setDarkTheme] = useState<boolean>(true);
    const navTitleContext = { title, setTitle };
    const darkThemeContext = { darkTheme, setDarkTheme };

    const theme = React.useMemo(
        () => createMuiTheme({
            palette: {
                type: darkTheme ? 'dark' : 'light',
            },
        }),
        [darkTheme],
    );

    return (
        <Router>

            <ThemeProvider theme={theme}>
                <NavBarTitle.Provider value={navTitleContext}>
                    <CssBaseline />
                    <DarkTheme.Provider value={darkThemeContext}>
                        <NavBar />
                    </DarkTheme.Provider>
                    <Container maxWidth={false} disableGutters style={{ padding: '5px' }}>
                        <Switch>
                            <Route path="/sources/:sourceId/search/">
                                <Search />
                            </Route>
                            <Route path="/extensions">
                                <Extensions />
                            </Route>
                            <Route path="/sources/:sourceId/popular/">
                                <MangaList popular />
                            </Route>
                            <Route path="/sources/:sourceId/latest/">
                                <MangaList popular={false} />
                            </Route>
                            <Route path="/sources">
                                <Sources />
                            </Route>
                            <Route path="/manga/:mangaId/chapter/:chapterId">
                                <Reader />
                            </Route>
                            <Route path="/manga/:id">
                                <Manga />
                            </Route>
                            <Route path="/">
                                <Home />
                            </Route>
                        </Switch>
                    </Container>
                </NavBarTitle.Provider>
            </ThemeProvider>
        </Router>
    );
}
