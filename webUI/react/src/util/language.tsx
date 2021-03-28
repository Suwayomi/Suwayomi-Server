/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

export const ISOLanguages = [
    { code: 'all', name: 'All', nativeName: 'All' },
    { code: 'installed', name: 'Installed', nativeName: 'Installed' },
    { code: 'updates pending', name: 'Updates pending', nativeName: 'Updates pending' },

    // full list: https://github.com/meikidd/iso-639-1/blob/master/src/data.js
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
    { code: 'zu', name: 'Zulu', nativeName: 'isiZulu' },
    { code: 'xh', name: 'Xhosa', nativeName: 'isiXhosa' },
    { code: 'uk', name: 'Ukrainian', nativeName: 'Українська' },
    { code: 'ro', name: 'Romanian', nativeName: 'Română' },
    { code: 'bg', name: 'Bulgarian', nativeName: 'български' },
    { code: 'cs', name: 'Czech', nativeName: 'čeština' },
    { code: 'pl', name: 'Polish', nativeName: 'polski' },
    { code: 'no', name: 'Norwegian', nativeName: 'Norsk' },
    { code: 'nl', name: 'Dutch', nativeName: 'Nederlands' },
    { code: 'my', name: 'Burmese', nativeName: 'ဗမာစာ' },
    { code: 'ms', name: 'Malay', nativeName: 'Malaysia' },
    { code: 'mn', name: 'Mongolian', nativeName: 'Монгол' },
    { code: 'ml', name: 'Malayalam', nativeName: 'മലയാളം' },
    { code: 'ku', name: 'Kurdish', nativeName: 'Kurdî' },
    { code: 'hu', name: 'Hungarian', nativeName: 'Magyar' },
    { code: 'hr', name: 'Croatian', nativeName: 'Hrvatski' },
    { code: 'he', name: 'Hebrew', nativeName: 'עברית' },
    { code: 'fil', name: 'Filipino', nativeName: 'Filipino' },
    { code: 'fi', name: 'Finnish', nativeName: 'suomi' },
    { code: 'fa', name: 'Persian', nativeName: 'فارسی' },
    { code: 'eu', name: 'Basque', nativeName: 'euskara' },
    { code: 'el', name: 'Greek', nativeName: 'Ελληνικά' },
    { code: 'da', name: 'Danish', nativeName: 'dansk' },
];

export function langCodeToName(code: string): string {
    const whereToCut = code.indexOf('-') !== -1 ? code.indexOf('-') : code.length;

    const proccessedCode = code.toLocaleLowerCase().substring(0, whereToCut);
    let result = 'Error';

    for (let i = 0; i < ISOLanguages.length; i++) {
        if (ISOLanguages[i].code === proccessedCode) result = ISOLanguages[i].nativeName;
    }

    if (code.indexOf('-') !== -1) {
        result = `${result} (${code.substring(whereToCut + 1)})`;
    }

    return result;
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
