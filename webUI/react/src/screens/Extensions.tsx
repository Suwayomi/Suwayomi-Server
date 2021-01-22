import React, { useContext, useEffect, useState } from 'react';
import ExtensionCard from '../components/ExtensionCard';
import NavBarTitle from '../context/NavbarTitle';

export default function Extensions() {
    const { setTitle } = useContext(NavBarTitle);
    setTitle('Extensions');
    const [extensions, setExtensions] = useState<IExtension[]>([]);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/extension/list')
            .then((response) => response.json())
            .then((data) => setExtensions(data));
    }, []);

    if (extensions.length === 0) {
        return <h3>wait</h3>;
    }
    return <>{extensions.map((it) => <ExtensionCard extension={it} />)}</>;
}
