#!/bin/bash

cp ../master/repo/server-r* .
new_build=$(ls | tail -1)
echo "New build file name: $new_build"

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
