import React, { useState } from 'react';
import {
    BrowserRouter as Router,
    Switch,
    Route,
} from 'react-router-dom';
import Button from '@material-ui/core/Button';
import NavBar from './components/NavBar';
import ExtensionCard from './components/ExtensionCard';
import SourceCard from './components/SourceCard';

function Extensions() {
    let mapped;
    const [extensions, setExtensions] = useState<IExtension[]>([]);

    if (extensions.length === 0) {
        mapped = <h3>wait</h3>;
        fetch('http://127.0.0.1:4567/api/v1/extension/list')
            .then((response) => response.json())
            .then((data) => setExtensions(data));
    } else {
        mapped = extensions.map((it) => <ExtensionCard extension={it} />);
    }

    return <h2>{mapped}</h2>;
}

function Sources() {
    let mapped;
    const [sources, setSources] = useState<ISource[]>([]);

    if (sources.length === 0) {
        mapped = <h3>wait</h3>;
        fetch('http://127.0.0.1:4567/api/v1/source/list')
            .then((response) => response.json())
            .then((data) => setSources(data));
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
