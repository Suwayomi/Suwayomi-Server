function getItem<T>(key: string, defaultValue: T) : T {
    try {
        const item = window.localStorage.getItem(key);

        if (item !== null) { return JSON.parse(item); }

        window.localStorage.setItem(key, JSON.stringify(defaultValue));
    } finally {
        // eslint-disable-next-line no-unsafe-finally
        return defaultValue;
    }
}

function setItem<T>(key: string, value: T): void {
    try {
        window.localStorage.setItem(key, JSON.stringify(value));

        // eslint-disable-next-line no-empty
    } catch (error) { }
}

export default { getItem, setItem };
