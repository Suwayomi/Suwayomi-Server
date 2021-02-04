#!/bin/bash

git lfs install
#git lfs track "*.zip"

cp ../master/repo/* .
new_jar_build=$(ls *.jar| tail -1)
echo "last jar build file name: $new_jar_build"

new_win32_build=$(ls *.zip| tail -1)
echo "last win32 build file name: $new_win32_build"

cp -f $new_jar_build Tachidesk-latest.jar
cp -f $new_win32_build Tachidesk-latest-win32.zip

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
