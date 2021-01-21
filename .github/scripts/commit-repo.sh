#!/bin/bash

cp ../master/repo/server-r* .
new_build=$(ls | tail -1)
echo "New build file name: $new_build"

# every build generates different jar even if same code, so comment this out.
#diff $new_build server-latest.jar > /dev/null
#if [ $? -eq 1 ]; then
#  echo "This is different to latest, replace latest."
#  cp -f $new_build server-latest.jar
#else
#  echo "This is the same as latest, throw it away."
#  rm $new_build
#fi


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
