import React, {useState} from "react";
import {
    BrowserRouter as Router,
    Switch,
    Route,
    Link
} from "react-router-dom";
import Button from '@material-ui/core/Button';
import TemporaryDrawer from "./components/TemporaryDrawer";
import NavBar from "./components/NavBar";


export default function App() {
    return (
        <Router>
            {/*<TemporaryDrawer/>*/}
            <NavBar/>

            <Switch>
                <Route path="/extensions">
                    <Extensions/>
                </Route>
                <Route path="/users">
                    <Users/>
                </Route>
                <Route path="/">
                    <Home/>
                </Route>
            </Switch>
        </Router>
    );
}

function Extensions() {
    let mapped;
    let [extensions, setExtensions] = useState([])

    if (extensions.length === 0) {
        mapped = <h3>wait</h3>;
        fetch("http://127.0.0.1:4567/api/v1/extensions")
            .then(response => response.json())
            .then(data => setExtensions(data));
    } else {
        mapped = extensions.map(it => <h3>{it.name}</h3>);
    }

    return <h2>{mapped}</h2>;
}

function Home() {
    return (
        <Button variant="contained" color="primary">
            Hello World
        </Button>
    )
}

function Users() {
    return <h2>Users</h2>;
}
