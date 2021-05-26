#!/bin/sh
if [ ! -f /home/arbuilder/.local/share/Tachidesk/docker_touchfile ]; then
	touch /home/arbuilder/.local/share/Tachidesk/docker_touchfile
	curl -s --create-dirs -L https://raw.githubusercontent.com/suwayomi/tachidesk/master/docker/server.conf -o /home/arbuilder/.local/share/Tachidesk/server.conf;
fi
echo ""
echo ""
echo "                                                                ************README***********"
echo "                                         Read Readme from https://github.com/suwayomi/docker-tachidesk before running container"
echo "                                                                *****************************"
echo ""
echo ""
echo "The server is running on http://localhost:4567"
echo "log file location inside the container  /home/arbuilder/.local/share/Tachidesk/logfile.log"
exec java -jar "/home/arbuilder/startup/tachidesk_latest.jar" > /home/arbuilder/.local/share/Tachidesk/logfile.log 2>&1;