// Replaces electron's default app, which asks before granting any permission (e.g. "screen-wake-lock" in the reader)
const { app, BrowserWindow, shell } = require('electron')

const appUrl = process.argv.at(-1)
// only what the WebUI uses
const grantedPermissions = ['screen-wake-lock', 'fullscreen', 'clipboard-sanitized-write']

app.whenReady().then(() => {
  const window = new BrowserWindow({ autoHideMenuBar: true })
  window.webContents.session.setPermissionRequestHandler((webContents, permission, callback, details) =>
    callback(
      grantedPermissions.includes(permission) &&
        URL.parse(details.requestingUrl)?.origin === new URL(appUrl).origin,
    ),
  )
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (['http:', 'https:'].includes(URL.parse(url)?.protocol)) {
      shell.openExternal(url)
    }
    return { action: 'deny' }
  })
  window.loadURL(appUrl)
})
