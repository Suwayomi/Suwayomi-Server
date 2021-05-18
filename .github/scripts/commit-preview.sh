#!/bin/bash

rm -rf preview/*.jar preview/*.zip

cp master/server/build/Tachidesk-*.jar preview
cp master/server/build/Tachidesk-*.zip preview

cd preview

new_jar_build=$(ls Tachidesk-*.jar)
echo "last jar build file name: $new_jar_build"

latest=$(echo $new_jar_build | sed -e's/Tachidesk-\|.jar//g')
echo "{ \"latest\": \"$latest\" }" > index.json

git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"
git status
if [ -n "$(git status --porcelain)" ]; then
    git add .
    git commit -m "Update preview repository"
    git push
else
    echo "No changes to commit"
fi
