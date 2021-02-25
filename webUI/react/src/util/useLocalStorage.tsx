/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { useState, Dispatch, SetStateAction } from 'react';

// eslint-disable-next-line max-len
export default function useLocalStorage<T>(key: string, initialValue: T) : [T, Dispatch<SetStateAction<T>>] {
    // State to store our value
    // Pass initial state function to useState so logic is only executed once
    const [storedValue, setStoredValue] = useState<T>(() => {
        try {
        // Get from local storage by key
            const item = window.localStorage.getItem(key);

            // Parse stored json or if null return set and return initialValue
            if (item !== null) { return JSON.parse(item); }

            window.localStorage.setItem(key, JSON.stringify(initialValue));
        } finally {
            // eslint-disable-next-line no-unsafe-finally
            return initialValue;
        }
    });

    // Return a wrapped version of useState's setter function that ...
    // ... persists the new value to localStorage.
    const setValue = (value: T | ((prevState: T) => T)) => {
        try {
        // Allow value to be a function so we have same API as useState
            const valueToStore = value instanceof Function ? value(storedValue) : value;
            // Save state
            setStoredValue(valueToStore);
            // Save to local storage
            window.localStorage.setItem(key, JSON.stringify(valueToStore));

        // eslint-disable-next-line no-empty
        } catch (error) { }
    };

    return [storedValue, setValue];
}
