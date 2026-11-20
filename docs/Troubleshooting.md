# Troubleshooting

This page is laid out in several sections, where each section describes a specific problem, followed by one or more possible solutions.

At the end, you will find a General section, which is the nuclear option if nothing else works.

For further support, visit the [official Suwayomi Discord server](https://discord.gg/DDZdqZWaHA).  
In such cases, it will be helpful to have logs ready. You can find them in [The Data Directory](./The-Data-Directory) in the logs directory.

**All steps below assume that you have stopped Suwayomi**.


## Broken database

- `failed due to
org.jetbrains.exposed.exceptions.ExposedSQLException: org.h2.jdbc.JdbcSQLSyntaxErrorException: Column "CATEGORY.SORT_ORDER" not found`
- `org.h2.jdbc.JdbcSQLSyntaxErrorException: Column "CHAPTER.KOREADER_HASH" not found`
- `java.lang.IllegalStateException: Unable to read the page at position 96170708817765466`
- Any other error text that includes "SQL Statement"

Your database is either corrupted or incompatible.

One of these is the cause:
- You were running a preview version and decided to downgrade to stable.
- You did not shut down Suwayomi properly.
- Suwayomi crashed in an unexpected way.

Solutions:
- If you downgraded, upgrade to preview again.
- Otherwise, you will need to reset and restore from a backup. See [General Troubleshooting](#general-troubleshooting) below.


## `HTTP error 429`

The source (or, if trackers are enabled, possibly the tracker) has blocked you for sending too many requests.  
Note that Mass-Migration can result in an unexpectedly high number of requests to both the source and any configured trackers.

Solution: Use other/more sources, download less, and wait between request-heavy actions.


## Extension times out

- `Timed out waiting for 20000 ms…`
- `Timed out waiting for page list`

First, check if this is an extension issue or a Suwayomi issue.
On the manga page of the problematic entry, click "Open in WebView".

Solutions:
- If the WebView loads: The issue is with the extension. Search [the issues](https://github.com/Suwayomi/Suwayomi-Server/issues) and discord if there are known problems with that extension.
- If the WebView errors: Go to [The Data Directory](./The-Data-Directory) and remove the `bin` and `cache` folders.
    - If the WebView still does not work after a restart, your installation is incomplete. On Linux, refer to [the README](https://github.com/Suwayomi/Suwayomi-Server#webview-support-gnulinux).


## Flaresolverr required

- `java.io.IOException: Cloudflare bypass currently disabled`

The source you are using has enabled CloudFlare's bot protection.
If you open the source's website in your browser, you should see the "Confirm I'm human" page.

Solution:
- Download and set up [Flaresolverr](https://github.com/FlareSolverr/FlareSolverr) or [Byparr](https://github.com/ThePhaseless/Byparr). Make sure to run Flaresolverr/Byparr every time you use this source.


## Flaresolverr not running

- `java.io.IOException: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:8191`

You have configured Flaresolverr by enabling the `server.flareSolverrEnabled` setting, but Flaresolverr is not installed and/or running.

Solutions:
- Install Flaresolverr if you haven't already (see previous section). Then ensure it is running (Windows: do not close the console window!).
- If it is running, ensure the configured url in `server.flareSolverrUrl` is correct. There is usually no need to change this.
- If it is running and the url is correct, check your firewall settings, your system may be blocking access to Flaresolverr.


## General Troubleshooting
This guide will try to fix Suwayomi by reseting it to a clean installation state.

> [!WARNING]
> This will remove all your data, including the library.
> Make sure you have copied your backups as described above!

- Make sure you have a recent backup of your library or create one in the app (if possible) because we **are going to wipe all Suwayomi data**.
- Make sure Suwayomi is not running (right click on tray icon and quit or kill it through the way your Operating System provides)
- Clear all browsing data on your browser if you use Suwayomi from a browser.
- Delete the Suwayomi data directory located below and re-run the app. See the article [The Data Directory](./The-Data-Directory) for information on how to find it.
    - If you wish to keep your downloads, you may also attempt to surgically remove only parts. You will need to remove `database.mv.db`, `database.trace.db`, `bin`, `cache`, `extensions`, `settings`, `webUI`. Removing only a subset of these files and folders may fail to resolve the problem.
- Open Suwayomi and go to Settings > Backup > Restore Backup, and select the latest backup you have.
    - Restoring from backup does not restore your downloads. If you chose to keep them in the above step, you will now need to re-download all manga. Suwayomi will pick up on the existing files and not actually download anything that isn't new.
- In the case that you have to periodically perform this fix or the problem persists or the method failed to fix it, open an issue or Join the [Suwayomi discord server](https://discord.gg/DDZdqZWaHA) to hang out with the community and to receive support and help.
