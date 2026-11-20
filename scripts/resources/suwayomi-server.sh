#!/bin/sh

export LD_PRELOAD="`realpath ./bin/catch_abort.so`"
exec ./jre/bin/java -jar ./bin/Suwayomi-Server.jar
