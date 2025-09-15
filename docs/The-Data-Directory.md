## Default data directory
The default data directory is located below depending on your operating system:

Replace `<Account>` with your account/username.

On Windows 7 and later : `C:\Users\<Account>\AppData\Local\Tachidesk`

On Windows XP : `C:\Documents and Settings\<Account>\Application Data\Local Settings\Tachidesk`

On Mac OS X : `/Users/<Account>/Library/Application Support/Tachidesk`

On Unix/Linux : `/home/<account>/.local/share/Tachidesk`

## Custom
You can set Suwayomi-Server to use a specific directory with the `-Dsuwayomi.tachidesk.config.server.rootDir` startup argument.

An example of this is `java -Dsuwayomi.tachidesk.config.server.rootDir="D:\Tachidesk Data" -jar Tachidesk-vX.Y.Z-rxxxx.jar`