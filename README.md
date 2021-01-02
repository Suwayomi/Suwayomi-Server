# Tachidesk
A not so much port of [Tachiyomi](https://tachiyomi.org/) to the web (and later Electron for the desktop experience)!

This project has two components: 
1. **server:** contains some of the original Tachiyomi code and serves a REST API
2. **webUI:** A react project that works with the server to do the presentation

## How do I run the thing?
### Getting things ready
run `$ scripts/getAndroid.sh` to do download Google's Android stubs jar.(do this only once)
### The Server
run `./gradlew :server:run` to run the server
### the webUI
how to do it is described in `webUI/react/README.md` but for short,
 first cd into `webUI/react` then run `yarn` to install the node modules(do this only once)
 then `yarn start` to start the client if a new browser window doesn't start automatically,
 then open `http://127.0.0.1:3000` in a modern browser.

## Can I use this application?
Checkout [the state of project](https://github.com/AriaMoradi/Tachidesk/issues/2) to see what's implemented.

## License

    Copyright (C) 2020 Aria Moradi

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
