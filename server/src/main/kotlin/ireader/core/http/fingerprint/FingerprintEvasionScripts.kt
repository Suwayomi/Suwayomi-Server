package ireader.core.http.fingerprint

/**
 * JavaScript injection scripts for browser fingerprint evasion
 * These scripts help avoid detection by anti-bot systems
 */
object FingerprintEvasionScripts {
    
    /**
     * Remove WebDriver detection flags
     */
    val webdriverEvasion = """
        (function() {
            // Remove webdriver flag
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined,
                configurable: true
            });
            
            // Remove automation flags (Chrome DevTools Protocol)
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
            
            // Remove Selenium flags
            delete window._selenium;
            delete window.__selenium_unwrapped;
            delete window.__webdriver_evaluate;
            delete window.__driver_evaluate;
            delete window.__webdriver_unwrapped;
            delete window.__driver_unwrapped;
            delete window.__fxdriver_evaluate;
            delete window.__fxdriver_unwrapped;
            delete window._Selenium_IDE_Recorder;
            delete window._phantom;
            delete window.__nightmare;
            delete window.callPhantom;
            delete window.callSelenium;
            delete window._WEBDRIVER_ELEM_CACHE;
            delete document.__webdriver_script_fn;
            delete document.__webdriver_script_func;
            delete document.__webdriver_script_function;
            delete document.${'$'}cdc_asdjflasutopfhvcZLmcfl_;
            delete document.${'$'}chrome_asyncScriptInfo;
            delete document.${'$'}wdc_;
        })();
    """.trimIndent()

    /**
     * Spoof navigator properties to match a real browser
     */
    val navigatorEvasion = """
        (function() {
            // Spoof plugins (Chrome on Android typically has these)
            const fakePlugins = {
                0: { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                1: { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '' },
                2: { name: 'Native Client', filename: 'internal-nacl-plugin', description: '' },
                length: 3,
                item: function(i) { return this[i]; },
                namedItem: function(name) { 
                    for (let i = 0; i < this.length; i++) {
                        if (this[i].name === name) return this[i];
                    }
                    return null;
                },
                refresh: function() {}
            };
            Object.defineProperty(navigator, 'plugins', { get: () => fakePlugins });
            
            // Spoof mimeTypes
            const fakeMimeTypes = {
                0: { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
                1: { type: 'text/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
                length: 2,
                item: function(i) { return this[i]; },
                namedItem: function(name) {
                    for (let i = 0; i < this.length; i++) {
                        if (this[i].type === name) return this[i];
                    }
                    return null;
                }
            };
            Object.defineProperty(navigator, 'mimeTypes', { get: () => fakeMimeTypes });
            
            // Spoof languages
            Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
            Object.defineProperty(navigator, 'language', { get: () => 'en-US' });
            
            // Spoof platform (Android)
            Object.defineProperty(navigator, 'platform', { get: () => 'Linux armv81' });
            
            // Spoof hardware concurrency (typical mobile value)
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
            
            // Spoof device memory (typical mobile value)
            Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
            
            // Spoof max touch points
            Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5 });
            
            // Spoof connection info
            if (navigator.connection) {
                Object.defineProperty(navigator.connection, 'effectiveType', { get: () => '4g' });
                Object.defineProperty(navigator.connection, 'rtt', { get: () => 50 });
                Object.defineProperty(navigator.connection, 'downlink', { get: () => 10 });
            }
        })();
    """.trimIndent()

    /**
     * Add subtle noise to canvas fingerprint
     */
    val canvasEvasion = """
        (function() {
            const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
            const originalToBlob = HTMLCanvasElement.prototype.toBlob;
            const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
            
            // Add noise to toDataURL
            HTMLCanvasElement.prototype.toDataURL = function(type) {
                if (this.width > 16 && this.height > 16) {
                    const context = this.getContext('2d');
                    if (context) {
                        try {
                            const imageData = originalGetImageData.call(context, 0, 0, this.width, this.height);
                            // Add very subtle noise (imperceptible but changes fingerprint)
                            for (let i = 0; i < imageData.data.length; i += 4) {
                                if (Math.random() < 0.01) { // Only modify 1% of pixels
                                    imageData.data[i] = imageData.data[i] ^ 1; // Flip least significant bit
                                }
                            }
                            context.putImageData(imageData, 0, 0);
                        } catch (e) {
                            // Canvas might be tainted, ignore
                        }
                    }
                }
                return originalToDataURL.apply(this, arguments);
            };
            
            // Add noise to toBlob
            HTMLCanvasElement.prototype.toBlob = function(callback, type, quality) {
                if (this.width > 16 && this.height > 16) {
                    const context = this.getContext('2d');
                    if (context) {
                        try {
                            const imageData = originalGetImageData.call(context, 0, 0, this.width, this.height);
                            for (let i = 0; i < imageData.data.length; i += 4) {
                                if (Math.random() < 0.01) {
                                    imageData.data[i] = imageData.data[i] ^ 1;
                                }
                            }
                            context.putImageData(imageData, 0, 0);
                        } catch (e) {}
                    }
                }
                return originalToBlob.apply(this, arguments);
            };
        })();
    """.trimIndent()

    /**
     * Spoof WebGL vendor and renderer
     */
    val webglEvasion = """
        (function() {
            const getParameterProxyHandler = {
                apply: function(target, thisArg, args) {
                    const param = args[0];
                    
                    // UNMASKED_VENDOR_WEBGL (37445)
                    if (param === 37445) {
                        return 'Google Inc. (Qualcomm)';
                    }
                    // UNMASKED_RENDERER_WEBGL (37446)
                    if (param === 37446) {
                        return 'ANGLE (Qualcomm, Adreno (TM) 650, OpenGL ES 3.2)';
                    }
                    // VERSION
                    if (param === 7938) {
                        return 'WebGL 2.0 (OpenGL ES 3.0 Chromium)';
                    }
                    // SHADING_LANGUAGE_VERSION
                    if (param === 35724) {
                        return 'WebGL GLSL ES 3.00 (OpenGL ES GLSL ES 3.0 Chromium)';
                    }
                    
                    return Reflect.apply(target, thisArg, args);
                }
            };
            
            // Apply to WebGL1
            if (typeof WebGLRenderingContext !== 'undefined') {
                WebGLRenderingContext.prototype.getParameter = new Proxy(
                    WebGLRenderingContext.prototype.getParameter,
                    getParameterProxyHandler
                );
            }
            
            // Apply to WebGL2
            if (typeof WebGL2RenderingContext !== 'undefined') {
                WebGL2RenderingContext.prototype.getParameter = new Proxy(
                    WebGL2RenderingContext.prototype.getParameter,
                    getParameterProxyHandler
                );
            }
        })();
    """.trimIndent()

    /**
     * Spoof audio context fingerprint
     */
    val audioEvasion = """
        (function() {
            if (typeof AudioContext !== 'undefined' || typeof webkitAudioContext !== 'undefined') {
                const AudioContextClass = window.AudioContext || window.webkitAudioContext;
                const originalCreateOscillator = AudioContextClass.prototype.createOscillator;
                const originalCreateDynamicsCompressor = AudioContextClass.prototype.createDynamicsCompressor;
                
                // Add slight variation to audio processing
                AudioContextClass.prototype.createOscillator = function() {
                    const oscillator = originalCreateOscillator.apply(this, arguments);
                    const originalConnect = oscillator.connect;
                    oscillator.connect = function(destination) {
                        // Add tiny frequency variation
                        if (oscillator.frequency && oscillator.frequency.value) {
                            oscillator.frequency.value += (Math.random() - 0.5) * 0.0001;
                        }
                        return originalConnect.apply(this, arguments);
                    };
                    return oscillator;
                };
            }
        })();
    """.trimIndent()
    
    /**
     * Spoof screen properties
     */
    val screenEvasion = """
        (function() {
            // Common mobile screen dimensions
            const screenWidth = 412;
            const screenHeight = 915;
            const availWidth = 412;
            const availHeight = 857;
            const colorDepth = 24;
            const pixelDepth = 24;
            
            Object.defineProperty(screen, 'width', { get: () => screenWidth });
            Object.defineProperty(screen, 'height', { get: () => screenHeight });
            Object.defineProperty(screen, 'availWidth', { get: () => availWidth });
            Object.defineProperty(screen, 'availHeight', { get: () => availHeight });
            Object.defineProperty(screen, 'colorDepth', { get: () => colorDepth });
            Object.defineProperty(screen, 'pixelDepth', { get: () => pixelDepth });
            
            // Spoof window dimensions
            Object.defineProperty(window, 'innerWidth', { get: () => screenWidth });
            Object.defineProperty(window, 'innerHeight', { get: () => availHeight });
            Object.defineProperty(window, 'outerWidth', { get: () => screenWidth });
            Object.defineProperty(window, 'outerHeight', { get: () => screenHeight });
        })();
    """.trimIndent()

    /**
     * Prevent permission detection
     */
    val permissionsEvasion = """
        (function() {
            if (navigator.permissions) {
                const originalQuery = navigator.permissions.query;
                navigator.permissions.query = function(parameters) {
                    // Return 'prompt' for notification permission (common for real users)
                    if (parameters.name === 'notifications') {
                        return Promise.resolve({ state: 'prompt', onchange: null });
                    }
                    return originalQuery.apply(this, arguments);
                };
            }
        })();
    """.trimIndent()
    
    /**
     * Spoof battery API (if available)
     */
    val batteryEvasion = """
        (function() {
            if (navigator.getBattery) {
                navigator.getBattery = function() {
                    return Promise.resolve({
                        charging: true,
                        chargingTime: 0,
                        dischargingTime: Infinity,
                        level: 0.87,
                        onchargingchange: null,
                        onchargingtimechange: null,
                        ondischargingtimechange: null,
                        onlevelchange: null
                    });
                };
            }
        })();
    """.trimIndent()
    
    /**
     * Combined full evasion script
     * Inject this before page load for maximum effectiveness
     */
    val fullEvasion: String by lazy {
        listOf(
            webdriverEvasion,
            navigatorEvasion,
            canvasEvasion,
            webglEvasion,
            audioEvasion,
            screenEvasion,
            permissionsEvasion,
            batteryEvasion
        ).joinToString("\n\n")
    }
    
    /**
     * Minimal evasion script (faster, less comprehensive)
     * Use when performance is critical
     */
    val minimalEvasion: String by lazy {
        listOf(
            webdriverEvasion,
            navigatorEvasion
        ).joinToString("\n\n")
    }
}
