import React, { useEffect, useState } from 'react';
import SourceCard from '../components/SourceCard';

export default function Sources() {
    let mapped;
    const [sources, setSources] = useState<ISource[]>([]);

    useEffect(() => {
        fetch('http://127.0.0.1:4567/api/v1/source/list')
            .then((response) => response.json())
            .then((data) => setSources(data));
    }, []);

    if (sources.length === 0) {
        mapped = <h3>wait</h3>;
    } else {
        mapped = sources.map((it) => <SourceCard source={it} />);
    }

    return <h2>{mapped}</h2>;
}
