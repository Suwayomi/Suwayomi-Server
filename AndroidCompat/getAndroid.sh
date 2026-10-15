#!/usr/bin/env bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

# This is a bash script to create android.jar stubs
set -euo pipefail

abort=no
for dep in "curl" "base64" "zip" "unzip" "sha256sum"
do
  command -v "$dep" >/dev/null 2>&1 || { echo >&2 "Error: This script needs $dep installed."; abort=yes; }
done

if [ "$abort" = yes ]; then
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
pushd "tmp" >/dev/null

# The URL is pinned to a fixed commit, so the payload is immutable.
ANDROID_JAR_SHA256="2f0e6e4ec44e95beefe0c041541afcec4f55f8b964b33599a987efb6e972f990"
curl -fsSL "https://android.googlesource.com/platform/prebuilts/sdk/+/6cd31be5e4e25901aadf838120d71a79b46d9add/30/public/android.jar?format=TEXT" | base64 --decode > android.jar

echo "$ANDROID_JAR_SHA256  android.jar" | sha256sum --check -
unzip -tq android.jar > /dev/null

# We need to remove any stub classes that we have implementations for
echo "Patching JAR..."

# `zip --delete` exits 12 ("nothing to do") when no pattern matches; that is
# expected (most source files are not Android classes), real errors abort.
zip_delete() {
    zip --delete "$@" >/dev/null 2>&1 || [ "$?" -eq 12 ]
}

echo "Removing org.json..."
zip_delete android.jar 'org/json/*'

echo "Removing org.apache..."
zip_delete android.jar 'org/apache/*'

echo "Removing org.w3c..."
zip_delete android.jar 'org/w3c/*'

echo "Removing org.xml..."
zip_delete android.jar 'org/xml/*'

echo "Removing org.xmlpull..."
zip_delete android.jar 'org/xmlpull/*'

echo "Removing junit..."
zip_delete android.jar 'junit/*'

echo "Removing javax..."
zip_delete android.jar 'javax/*'

echo "Removing java..."
zip_delete android.jar 'java/*'

echo "Removing overridden classes..."
zip_delete android.jar android/app/Application.class
zip_delete android.jar android/app/Service.class
zip_delete android.jar android/net/Uri.class
zip_delete android.jar 'android/net/Uri$Builder.class'
zip_delete android.jar android/os/Environment.class
zip_delete android.jar android/text/format/Formatter.class
zip_delete android.jar android/text/Html.class

# Dedup overridden Android classes
# (portable realpath: $(pwd) is already absolute here)
ABS_JAR="$(pwd)/android.jar"
function dedup() {
    pushd "$1" >/dev/null
    CLASSES="$(find ./* -type f)"
    echo "$CLASSES" | while read -r class
    do
        # strip the leading "./" so the name matches zip entry paths
        NAME="${class#./}"
        NAME="${NAME%.*}"
        echo "Processing class: $NAME"
        zip_delete "$ABS_JAR" "$NAME.class" "$NAME\$*.class" "${NAME}Kt.class" "${NAME}Kt\$*.class"
    done
    popd >/dev/null
}

popd >/dev/null
dedup AndroidCompat/src/main/java
dedup server/src/main/kotlin

echo "Copying Android.jar to library folder..."
mkdir -p AndroidCompat/lib
mv tmp/android.jar AndroidCompat/lib/android.jar

echo "Cleaning up..."
rm -rf "tmp"

echo "Done!"
