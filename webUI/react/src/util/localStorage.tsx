/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

function getItem<T>(key: string, defaultValue: T) : T {
    try {
        const item = window.localStorage.getItem(key);

        if (item !== null) {
            return JSON.parse(item);
        }

        window.localStorage.setItem(key, JSON.stringify(defaultValue));

        /* eslint-disable no-empty */
    } finally { }
    return defaultValue;
}

function setItem<T>(key: string, value: T): void {
    try {
        window.localStorage.setItem(key, JSON.stringify(value));

        // eslint-disable-next-line no-empty
    } finally { }
}

export default { getItem, setItem };
