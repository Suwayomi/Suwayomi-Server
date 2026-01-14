#!/bin/bash

# exit early in case the file already exists
if [ -f /home/suwayomi/.local/share/Tachidesk/server.conf ]; then
    exit 0
fi

mkdir -p /home/suwayomi/.local/share/Tachidesk

# extract the server reference config from the jar
unzip -q -j /home/suwayomi/startup/tachidesk_latest.jar "server-reference.conf" -d /home/suwayomi/startup

# move and rename the reference config
mv /home/suwayomi/startup/server-reference.conf /home/suwayomi/.local/share/Tachidesk/server.conf
