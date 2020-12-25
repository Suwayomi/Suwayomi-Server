import React, { useEffect, useState } from 'react';
import {
    BrowserRouter as Router,
    Switch,
    Route,
} from 'react-router-dom';
import Button from '@material-ui/core/Button';
import NavBar from './components/NavBar';
import ExtensionCard from './components/ExtensionCard';
import SourceCard from './components/SourceCard';
import MangaCard from './components/MangaCard';

function MangaPage() {
    let mapped;
    const [mangas, setMangas] = useState<IManga[]>([]);

    useEffect(() => {
        fetch('https://picsum.photos/v2/list')
            .then((response) => response.json())
            .then((data: { author: string, download_url: string }[]) => setMangas(
                data.map((it) => ({ name: it.author, imageUrl: it.download_url })),
            ));
    });

    if (mangas.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, auto)', gridGap: '1em' }}>
                {mangas.map((it) => (
                    <MangaCard manga={it} />
                ))}
            </div>
        );
    }

    return mapped;
}

function Extensions() {
    let mapped;
    const [extensions, setExtensions] = useState<IExtension[]>([]);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/extension/list')
            .then((response) => response.json())
            .then((data) => setExtensions(data));
    });

    if (extensions.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = extensions.map((it) => <ExtensionCard extension={it} />);
    }

    return <h2>{mapped}</h2>;
}

function Sources() {
    let mapped;
    const [sources, setSources] = useState<ISource[]>([]);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/source/list')
            .then((response) => response.json())
            .then((data) => setSources(data));
    });

    if (sources.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = sources.map((it) => <SourceCard source={it} />);
    }

    return <h2>{mapped}</h2>;
}

function Home() {
    return (
        <Button variant="contained" color="primary">
            Hello World
        </Button>
    );
}

export default function App() {
    return (
        <Router>
            {/* <TemporaryDrawer/> */}
            <NavBar />

            <Switch>
                <Route path="/mangapage">
                    <MangaPage />
                </Route>
                <Route path="/extensions">
                    <Extensions />
                </Route>
                <Route path="/sources">
                    <Sources />
                </Route>
                <Route path="/">
                    <Home />
                </Route>
            </Switch>
        </Router>
    );
}
