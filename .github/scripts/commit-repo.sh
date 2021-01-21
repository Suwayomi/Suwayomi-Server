#!/bin/bash

cp ../master/repo/* .
new_build=$(ls | tail -1)
echo "New build file name: $new_build"

cp -f $new_build Tachidesk-latest.jar

git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update repo"
    git push
else
    echo "No changes to commit"
fi
