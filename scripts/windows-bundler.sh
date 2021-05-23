#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

if [ $1 = "win32" ]; then
  jre="OpenJDK8U-jre_x86-32_windows_hotspot_8u292b10.zip"
  arch="win32"
  electron="electron-v12.0.9-win32-ia32.zip"
else
  jre="OpenJDK8U-jre_x64_windows_hotspot_8u292b10.zip"
  arch="win64"
  electron="electron-v12.0.9-win32-x64.zip"
fi

jre_dir="jdk8u292-b10-jre"

echo "creating windows bundle"

jar=$(ls ../server/build/Tachidesk-*.jar)
jar_name=$(echo $jar | cut -d'/' -f4)
release_name=$(echo $jar_name | cut -d'.' -f4 --complement)-$arch


# make release dir
mkdir $release_name


echo "Dealing with jre..."
if [ ! -f $jre ]; then
  curl -L "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u292-b10/$jre" -o $jre
fi
unzip $jre
mv $jre_dir $release_name/jre

echo "Dealing with electron"
if [ ! -f $electron ]; then
  curl -L "https://github.com/electron/electron/releases/download/v12.0.9/$electron" -o $electron
fi
unzip $electron -d $release_name/electron

# copy artifacts
cp $jar $release_name/Tachidesk.jar
cp "resources/Tachidesk Launcher-$arch.exe" "$release_name/Tachidesk Launcher.exe"
cp "resources/Tachidesk Launcher.bat" $release_name
cp "resources/Tachidesk Debug Launcher.bat" $release_name
cp "resources/Tachidesk Electron Launcher.bat" $release_name

zip_name=$release_name.zip
zip -9 -s90 -r $zip_name $release_name

rm -rf $release_name


# clean up from possible previous runs
if [ -f ../server/build/$zip_name ]; then
  rm ../server/build/$release_name.z*
fi

mv $release_name.z* ../server/build/
