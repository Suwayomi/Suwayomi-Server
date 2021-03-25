/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useState, Dispatch, SetStateAction } from 'react';
import storage from './localStorage';

// eslint-disable-next-line max-len
export default function useLocalStorage<T>(key: string, defaultValue: T | (() => T)) : [T, Dispatch<SetStateAction<T>>] {
    const initialState = defaultValue instanceof Function ? defaultValue() : defaultValue;
    const [storedValue, setStoredValue] = useState<T>(storage.getItem(key, initialState));

    const setValue = ((value: T | ((prevState: T) => T)) => {
        // Allow value to be a function so we have same API as useState
        const valueToStore = value instanceof Function ? value(storedValue) : value;
        setStoredValue(valueToStore);
        storage.setItem(key, valueToStore);
    }) as React.Dispatch<React.SetStateAction<T>>;

    return [storedValue, setValue];
}
