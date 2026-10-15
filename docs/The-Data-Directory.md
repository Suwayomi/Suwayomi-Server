## Default data directory
The default data directory is located below depending on your operating system:

Replace `<Account>` with your account/username.

On Windows 7 and later : `C:\Users\<Account>\AppData\Local\Tachidesk`

On Mac OS X : `/Users/<Account>/Library/Application Support/Tachidesk`

On Unix/Linux : `/home/<account>/.local/share/Tachidesk`

## Custom
You can set Suwayomi-Server to use a specific directory with the `-Dsuwayomi.tachidesk.config.server.rootDir` startup argument.

An example of this is `java -Dsuwayomi.tachidesk.config.server.rootDir="D:\Tachidesk Data" -jar Suwayomi-Server-vX.Y.Z-rxxxx.jar`

## Protecting the data directory

Several files in the data directory contain sensitive material and must not
be shared or world-readable:

- `server.conf` — the server credentials (`server.authUsername` / `server.authPassword`)
  and any tracker OAuth client settings.
- `settings/tracker.xml` — tracker OAuth tokens, stored in plaintext.
- `settings/cookie_store.xml` — persisted session cookies for sources and trackers.
- `settings/jwt.xml`, `settings/sync.xml`, `settings/koreader_sync.xml` — JWT
  signing state and sync-service credentials/tokens.
- `database.mv.db` (and `database.mv.db.200.backup` / `database.mv.db.200.backup.1`) —
  the full library database, including per-account data.
- `backups/` — `.tachibk` library backup files; they contain the library and,
  for privileged users, server settings.

On POSIX filesystems the server writes the `settings/` preference files with
owner-only (`0600`) permissions, but deployments should still treat the whole
data directory as secret: do not expose it to other users or processes, and
apply the same care when copying, sharing, or backing it up.