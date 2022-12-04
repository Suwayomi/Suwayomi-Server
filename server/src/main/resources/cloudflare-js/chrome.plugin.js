(function () {
    const plugin0 = Object.create(Plugin.prototype);

    const mimeType0 = Object.create(MimeType.prototype);
    const mimeType1 = Object.create(MimeType.prototype);
    Object.defineProperties(mimeType0, {
        type: {
            get: () => 'application/pdf',
        },
        suffixes: {
            get: () => 'pdf',
        },
    });

    Object.defineProperties(mimeType1, {
        type: {
            get: () => 'text/pdf',
        },
        suffixes: {
            get: () => 'pdf',
        },
    });

    Object.defineProperties(plugin0, {
        name: {
            get: () => 'Chrome PDF Viewer',
        },
        description: {
            get: () => 'Portable Document Format',
        },
        0: {
            get: () => {
                return mimeType0;
            },
        },
        1: {
            get: () => {
                return mimeType1;
            },
        },
        length: {
            get: () => 2,
        },
        filename: {
            get: () => 'internal-pdf-viewer',
        },
    });

    const plugin1 = Object.create(Plugin.prototype);
    Object.defineProperties(plugin1, {
        name: {
            get: () => 'Chromium PDF Viewer',
        },
        description: {
            get: () => 'Portable Document Format',
        },
        0: {
            get: () => {
                return mimeType0;
            },
        },
        1: {
            get: () => {
                return mimeType1;
            },
        },
        length: {
            get: () => 2,
        },
        filename: {
            get: () => 'internal-pdf-viewer',
        },
    });

    const plugin2 = Object.create(Plugin.prototype);
    Object.defineProperties(plugin2, {
        name: {
            get: () => 'Microsoft Edge PDF Viewer',
        },
        description: {
            get: () => 'Portable Document Format',
        },
        0: {
            get: () => {
                return mimeType0;
            },
        },
        1: {
            get: () => {
                return mimeType1;
            },
        },
        length: {
            get: () => 2,
        },
        filename: {
            get: () => 'internal-pdf-viewer',
        },
    });

    const plugin3 = Object.create(Plugin.prototype);
    Object.defineProperties(plugin3, {
        name: {
            get: () => 'PDF Viewer',
        },
        description: {
            get: () => 'Portable Document Format',
        },
        0: {
            get: () => {
                return mimeType0;
            },
        },
        1: {
            get: () => {
                return mimeType1;
            },
        },
        length: {
            get: () => 2,
        },
        filename: {
            get: () => 'internal-pdf-viewer',
        },
    });

    const plugin4 = Object.create(Plugin.prototype);
    Object.defineProperties(plugin4, {
        name: {
            get: () => 'WebKit built-in PDF',
        },
        description: {
            get: () => 'Portable Document Format',
        },
        0: {
            get: () => {
                return mimeType0;
            },
        },
        1: {
            get: () => {
                return mimeType1;
            },
        },
        length: {
            get: () => 2,
        },
        filename: {
            get: () => 'internal-pdf-viewer',
        },
    });

    const pluginArray = Object.create(PluginArray.prototype);

    pluginArray['0'] = plugin0;
    pluginArray['1'] = plugin1;
    pluginArray['2'] = plugin2;
    pluginArray['3'] = plugin3;
    pluginArray['4'] = plugin4;

    let refreshValue;

    Object.defineProperties(pluginArray, {
        length: {
            get: () => 5,
        },
        item: {
            value: (index) => {
                if (index > 4294967295) {
                    index = index % 4294967296;
                }
                switch (index) {
                    case 0:
                        return plugin3;
                    case 1:
                        return plugin0;
                    case 2:
                        return plugin1;
                    case 3:
                        return plugin2;
                    case 4:
                        return plugin4;
                    default:
                        break;
                }
            },
        },
        refresh: {
            get: () => {
                return refreshValue;
            },
            set: (value) => {
                refreshValue = value;
            },
        },
    });

    Object.defineProperty(Object.getPrototypeOf(navigator), 'plugins', {
        get: () => {
            return pluginArray;
        },
    });
})();
