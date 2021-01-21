#!/bin/bash
set -e

mkdir -p repo/

revision=$(git rev-list master --count)

cp server/build/server-1.0-all.jar "repo/server-r$revision.jar"