/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// Electron entry point for the WebUI.
// Used instead of Electron's default app, which shows a confirmation dialog for every permission request
// (e.g. "screen-wake-lock" requested by the reader).
const { app, BrowserWindow, shell } = require('electron')

const appUrl = process.argv.find((arg) => /^https?:\/\//.test(arg))

function isAppOrigin(url) {
  try {
    return new URL(url).origin === new URL(appUrl).origin
  } catch {
    return false
  }
}

app.whenReady().then(() => {
  const window = new BrowserWindow({
    width: 900,
    height: 600,
    autoHideMenuBar: true,
    show: false,
  })

  window.webContents.session.setPermissionRequestHandler((webContents, permission, callback, details) => {
    callback(isAppOrigin(details.requestingUrl ?? webContents.getURL()))
  })

  window.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) {
      shell.openExternal(url)
    }
    return { action: 'deny' }
  })

  window.once('ready-to-show', () => window.show())
  window.loadURL(appUrl)
})

app.on('window-all-closed', () => app.quit())
