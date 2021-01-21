#!/bin/bash
set -e

cp ../master/repo/server-r* .
new_build=$(ls | tail -1)
diff $new_build server-latest.jar > /dev/null
if [ "$?" -ne 0 ]; then # same file?
    rm $new_build
else
    cp -f $new_build server-latest.jar
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
