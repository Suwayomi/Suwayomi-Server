#!/bin/sh

if [ ! -f /usr/bin/electron ]; then
	echo "Electron executable was not found! 
In order to run this launcher, you need Electron installed."
	exit 1
fi

exec /usr/bin/java \
    -Dsuwayomi.tachidesk.config.server.webUIInterface=electron \
    -Dsuwayomi.tachidesk.config.server.electronPath=/usr/bin/electron \
    -jar /usr/share/java/tachidesk-server/tachidesk-server.jar
