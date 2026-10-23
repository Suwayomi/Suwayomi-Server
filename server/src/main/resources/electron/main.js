// Replaces electron's default app, which asks before granting any permission (e.g. "screen-wake-lock" in the reader)
const { app, BrowserWindow, shell } = require('electron')

const appUrl = process.argv.at(-1)

app.whenReady().then(() => {
  const window = new BrowserWindow({ autoHideMenuBar: true })
  window.webContents.session.setPermissionRequestHandler((webContents, permission, callback, details) =>
    callback(new URL(details.requestingUrl).origin === new URL(appUrl).origin),
  )
  window.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
  window.loadURL(appUrl)
})
