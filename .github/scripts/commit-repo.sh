#!/bin/bash
set -e

cp ../master/repo/server-r* .
new_build=$(ls | tail -1)

diff $new_build server-latest.jar
if [ "$?" -eq 1 ]; then
  cp -f $new_build server-latest.jar
else
  rm $new_build
fi


git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update extensions repo"
    git push
else
    echo "No changes to commit"
fi
