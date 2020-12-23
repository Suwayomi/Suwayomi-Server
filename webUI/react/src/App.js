import React, {useState} from "react";
import {
    BrowserRouter as Router,
    Switch,
    Route,
    Link
} from "react-router-dom";

export default function App() {
    return (
        <Router>
            <div>
                <nav>
                    <ul>
                        <li>
                            <Link to="/">Home</Link>
                        </li>
                        <li>
                            <Link to="/extensions">Extensions</Link>
                        </li>
                        <li>
                            <Link to="/users">Users</Link>
                        </li>
                    </ul>
                </nav>

                {/* A <Switch> looks through its children <Route>s and
            renders the first one that matches the current URL. */}
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
            </div>
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
    return <h2>Home</h2>;
}

function Users() {
    return <h2>Users</h2>;
}
