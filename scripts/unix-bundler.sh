#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

electron_version="v12.0.9"

if [ $1 = "linux-x64" ]; then
  jre="OpenJDK8U-jre_x64_linux_hotspot_8u302b08.tar.gz"
  jre_release="jdk8u302-b08"
  jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/$jre_release/$jre"
  jre_dir="$jre_release-jre"
  electron="electron-$electron_version-linux-x64.zip"
elif [ $1 = "macOS-x64" ]; then
  jre="OpenJDK8U-jre_x64_mac_hotspot_8u302b08.tar.gz"
  jre_release="jdk8u302-b08"
  jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/$jre_release/$jre"
  jre_dir="$jre_release-jre"
  electron="electron-$electron_version-darwin-x64.zip"
elif [ $1 = "macOS-arm64" ]; then
  jre="zulu8.56.0.23-ca-jre8.0.302-macosx_aarch64.tar.gz"
  jre_release="zulu8.56.0.23-ca-jre8.0.302-macosx_aarch64"
  jre_url="https://cdn.azul.com/zulu/bin/$jre"
  jre_dir="$jre_release"
  electron="electron-$electron_version-darwin-arm64.zip"
else
  echo "Unsupported arch value: $1"
  exit 1
fi

arch="$1"
os=$(echo $arch | cut -d '-' -f1)

echo "creating $arch bundle"

jar=$(ls ../server/build/*.jar | tail -n1)
jar_name=$(echo $jar | cut -d'/' -f4)
release_name=$(echo $jar_name | sed 's/.jar//')-$arch


# make release dir
mkdir $release_name


echo "Dealing with jre..."
if [ ! -f $jre ]; then
  curl -L $jre_url -o $jre
fi
tar xvf $jre
mv $jre_dir $release_name/jre

echo "Dealing with electron"
if [ ! -f $electron ]; then
  curl -L "https://github.com/electron/electron/releases/download/$electron_version/$electron" -o $electron
fi
unzip $electron -d $release_name/electron

# copy artifacts
cp $jar $release_name/Tachidesk.jar
if [ $os = linux ]; then
  cp "resources/tachidesk-browser-launcher.sh" $release_name
  cp "resources/tachidesk-debug-launcher.sh" $release_name
  cp "resources/tachidesk-electron-launcher.sh" $release_name
elif [ $os = macOS ]; then
  cp "resources/Tachidesk Browser Launcher.command" $release_name
  cp "resources/Tachidesk Debug Launcher.command" $release_name
  cp "resources/Tachidesk Electron Launcher.command" $release_name
fi

archive_name=""
if [ $os = linux ]; then
  archive_name=$release_name.tar.gz
  GZIP=-9 tar cvzf $archive_name $release_name
elif [ $os = macOS ]; then
  archive_name=$release_name.zip
  zip -9 -r $archive_name $release_name
fi

rm -rf $release_name

# clean up from possible previous runs
if [ -f ../server/build/$archive_name ]; then
  rm ../server/build/$archive_name
fi

mv $archive_name ../server/build/
