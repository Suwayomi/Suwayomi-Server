#!/bin/bash
set -e

mkdir -p repo/

revision=$(git rev-list master --count)
# add zero padding
revision=$(printf %04d $revision)

cp server/build/server-1.0-all.jar "repo/server-r$revision.jar"