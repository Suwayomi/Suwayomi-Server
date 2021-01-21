#!/bin/bash
set -e

mkdir -p repo/

revision=$(git rev-list HEAD --count)

cp server/build/server-1.0-all.jar "repo/server-r$revision.jar"
cp -f server/build/server-1.0-all.jar "repo/server-latest.jar"