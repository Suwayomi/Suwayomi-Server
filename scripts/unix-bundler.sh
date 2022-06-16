#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

electron_version="v14.0.0"

arch="$1"
os="$(echo $arch | cut -d '-' -f1)"
if [ "$arch" = "linux-all" ]; then
  # continue
elif [ "$arch" = "linux-x64" ]; then
  jre="OpenJDK8U-jre_x64_linux_hotspot_8u302b08.tar.gz"
  jre_release="jdk8u302-b08"
  jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/$jre_release/$jre"
  jre_dir="$jre_release-jre"
  electron="electron-$electron_version-linux-x64.zip"
elif [ "$arch" = "macOS-x64" ]; then
  jre="OpenJDK8U-jre_x64_mac_hotspot_8u302b08.tar.gz"
  jre_release="jdk8u302-b08"
  jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/$jre_release/$jre"
  jre_dir="$jre_release-jre"
  electron="electron-$electron_version-darwin-x64.zip"
elif [ "$arch" = "macOS-arm64" ]; then
  jre="zulu8.56.0.23-ca-jre8.0.302-macosx_aarch64.tar.gz"
  jre_release="zulu8.56.0.23-ca-jre8.0.302-macosx_aarch64"
  jre_url="https://cdn.azul.com/zulu/bin/$jre"
  jre_dir="$jre_release/zulu-8.jre"
  electron="electron-$electron_version-darwin-arm64.zip"
else
  echo "Unsupported arch value: $arch"
  exit 1
fi

echo "creating $arch bundle"
jar="$(ls ../server/build/*.jar | tail -n1)"
jar_name="$(echo $jar | cut -d'/' -f4)"
release_name="$(echo $jar_name | sed 's/.jar//')-$arch"
mkdir "$release_name"

# download jre and electron
if [ "$arch" != "linux-all" ]; then
  echo "Dealing with jre..."
  if [ ! -f "$jre" ]; then
    curl -L "$jre_url" -o "$jre"
  fi
  tar xvf "$jre"
  mv "$jre_dir" "$release_name/jre"

  echo "Dealing with electron"
  if [ ! -f "$electron" ]; then
    curl -L "https://github.com/electron/electron/releases/download/$electron_version/$electron" -o "$electron"
  fi
  unzip "$electron" -d "$release_name/electron"
fi

# copy artifacts
cp "$jar $release_name/tachidesk-server.jar"
if [ "$os" = "linux" ]; then
  cp "resources/tachidesk-server-browser-launcher.sh" "$release_name"
  cp "resources/tachidesk-server-debug-launcher.sh" "$release_name"
  cp "resources/tachidesk-server-electron-launcher.sh" "$release_name"
  cp "resources/tachidesk-server.desktop" "$release_name"
  cp "../server/src/main/resources/icon/faviconlogo.png" "$release_name/tachidesk-server.png"
  cp "resources/systemd"/* "$release_name" 
elif [ "$os" = "macOS" ]; then
  cp "resources/Tachidesk Browser Launcher.command" "$release_name"
  cp "resources/Tachidesk Debug Launcher.command" "$release_name"
  cp "resources/Tachidesk Electron Launcher.command" "$release_name"
fi

# archive then compress
archive_name=""
if [ "$os" = "linux" ]; then
  archive_name="$release_name.tar.gz"
  GZIP=-9 tar cvzf "$archive_name" "$release_name"
elif [ "$os" = "macOS" ]; then
  archive_name="$release_name.zip"
  zip -9 -r "$archive_name" "$release_name"
fi

# clean up from possible previous runs
if [ -f "../server/build/$archive_name" ]; then
  rm "../server/build/$archive_name"
fi

rm -rf "$release_name"
mv "$archive_name" "../server/build/"
