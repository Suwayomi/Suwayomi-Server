#!/usr/bin/env bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

# This is a bash script to create android.jar stubs

for dep in "curl" "base64" "zip"
do
  which $dep >/dev/null 2>&1 || { echo >&2 "Error: This script needs $dep installed."; abort=yes; }
done

if [ $abort = yes ]; then
  echo "Some of the dependencies didn't exist. Aborting."
  exit 1
fi


# foolproof against running from AndroidCompat dir instead of running from project root
if [ "$(basename "$(pwd)")" = "AndroidCompat" ]; then
  cd ..
fi


echo "Getting required Android.jar..."
rm -rf "tmp"
mkdir -p "tmp"
pushd "tmp" || exit

curl "https://android.googlesource.com/platform/prebuilts/sdk/+/3b8a524d25fa6c3d795afb1eece3f24870c60988/27/public/android.jar?format=TEXT" | base64 --decode > android.jar

# We need to remove any stub classes that we have implementations for
echo "Patching JAR..."

echo "Removing org.json..."
zip --delete android.jar org/json/*

echo "Removing org.apache..."
zip --delete android.jar org/apache/*

echo "Removing org.w3c..."
zip --delete android.jar org/w3c/*

echo "Removing org.xml..."
zip --delete android.jar org/xml/*

echo "Removing org.xmlpull..."
zip --delete android.jar org/xmlpull/*

echo "Removing junit..."
zip --delete android.jar junit/*

echo "Removing javax..."
zip --delete android.jar javax/*

echo "Removing java..."
zip --delete android.jar java/*

echo "Removing overridden classes..."
zip --delete android.jar android/app/Application.class
zip --delete android.jar android/app/Service.class
zip --delete android.jar android/net/Uri.class
zip --delete android.jar "android/net/Uri\$Builder.class"
zip --delete android.jar android/os/Environment.class
zip --delete android.jar android/text/format/Formatter.class
zip --delete android.jar android/text/Html.class

# Dedup overridden Android classes
ABS_JAR="$(realpath android.jar)"
function dedup() {
    pushd "$1" || exit
    CLASSES="$(find ./* -type f)"
    echo "$CLASSES" | while read -r class
    do
        NAME="${class%.*}"
        echo "Processing class: $NAME"
        zip --delete "$ABS_JAR" "$NAME.class" "$NAME\$*.class" "${NAME}Kt.class" "${NAME}Kt\$*.class" > /dev/null
    done
    popd || exit
}

popd || exit
dedup AndroidCompat/src/main/java
dedup server/src/main/kotlin

echo "Copying Android.jar to library folder..."
mv tmp/android.jar AndroidCompat/lib

echo "Cleaning up..."
rm -rf "tmp"

echo "Done!"
