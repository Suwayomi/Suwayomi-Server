import React, { useEffect, useState } from 'react';
import ExtensionCard from '../components/ExtensionCard';

export default function Extensions() {
    let mapped;
    const [extensions, setExtensions] = useState<IExtension[]>([]);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/extension/list')
            .then((response) => response.json())
            .then((data) => setExtensions(data));
    }, []);

    if (extensions.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = extensions.map((it) => <ExtensionCard extension={it} />);
    }

    return <h2>{mapped}</h2>;
}
