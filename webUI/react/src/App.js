import React, { useState } from 'react';
import {
    BrowserRouter as Router,
    Switch,
    Route,
} from 'react-router-dom';
import Button from '@material-ui/core/Button';
import NavBar from './components/NavBar';
import ExtensionCard from './components/ExtensionCard';

export default function App() {
    return (
        <Router>
            {/* <TemporaryDrawer/> */}
            <NavBar />

            <Switch>
                <Route path="/extensions">
                    <Extensions />
                </Route>
                <Route path="/users">
                    <Users />
                </Route>
                <Route path="/">
                    <Home />
                </Route>
            </Switch>
        </Router>
    );
}

function Extensions() {
    let mapped;
    const [extensions, setExtensions] = useState([]);

    if (extensions.length === 0) {
        mapped = <h3>wait</h3>;
        fetch('http://127.0.0.1:4567/api/v1/extensions')
            .then((response) => response.json())
            .then((data) => setExtensions(data));
    } else {
        mapped = extensions.map((it) => <ExtensionCard {...it} />);
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

function Users() {
    return <h2>Users</h2>;
}
