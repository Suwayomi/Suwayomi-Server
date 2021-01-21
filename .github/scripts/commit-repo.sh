#!/bin/bash

cp ../master/repo/server-r* .
new_build=$(ls | tail -1)
echo "$new_build"

diff $new_build server-latest.jar > /dev/null
if [ $? -eq 1 ]; then
  echo "copy"
  cp -f $new_build server-latest.jar
else
  echo "rm"
  rm $new_build
fi
echo "shit"


#git config --global user.email "github-actions[bot]@users.noreply.github.com"
#git config --global user.name "github-actions[bot]"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update repo"
    git push
else
    echo "No changes to commit"
fi
