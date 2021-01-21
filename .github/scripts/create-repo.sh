#!/bin/bash


mkdir -p repo/

# Get last commit message
last_commit_log=$(git log -1 --pretty=format:"%s")
echo "last commit log: $last_commit_log"

filter_count=$(echo "$last_commit_log" | grep -c "[RELEASE CI]" )

if [ "$filter_count" -gt 0 ]; then
  cp server/build/Tachidesk-*.jar repo/
fi