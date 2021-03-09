/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

export const ISOLanguages = [
    { code: 'all', name: 'All', nativeName: 'All' },
    { code: 'installed', name: 'Installed', nativeName: 'Installed' },

    { code: 'en', name: 'English', nativeName: 'English' },
    { code: 'ca', name: 'Catalan; Valencian', nativeName: 'Català' },
    { code: 'de', name: 'German', nativeName: 'Deutsch' },
    { code: 'es', name: 'Spanish; Castilian', nativeName: 'Español' },
    { code: 'fr', name: 'French', nativeName: 'Français' },
    { code: 'id', name: 'Indonesian', nativeName: 'Indonesia' },
    { code: 'it', name: 'Italian', nativeName: 'Italiano' },
    { code: 'pt', name: 'Portuguese', nativeName: 'Português' },
    { code: 'vi', name: 'Vietnamese', nativeName: 'Tiếng Việt' },
    { code: 'tr', name: 'Turkish', nativeName: 'Türkçe' },
    { code: 'ru', name: 'Russian', nativeName: 'русский' },
    { code: 'ar', name: 'Arabic', nativeName: 'العربية' },
    { code: 'hi', name: 'Hindi', nativeName: 'हिन्दी' },
    { code: 'th', name: 'Thai', nativeName: 'ไทย' },
    { code: 'zh', name: 'Chinese', nativeName: '中文' },
    { code: 'ja', name: 'Japanese', nativeName: '日本語' },
    { code: 'ko', name: 'Korean', nativeName: '한국어' },
];

export function langCodeToName(code: string): string {
    for (let i = 0; i < ISOLanguages.length; i++) {
        if (ISOLanguages[i].code === code) return ISOLanguages[i].nativeName;
    }
    return 'Error';
}

export function defualtLangs() {
    return [
        // todo: infer this from the browser
        'en',
    ];
}

export const langSortCmp = (a: string, b: string) => {
    // puts english first for convience
    const aLang = langCodeToName(a);
    const bLang = langCodeToName(b);

    if (a === 'en') return -1;
    if (b === 'en') return 1;
    return aLang > bLang ? 1 : -1;
};
