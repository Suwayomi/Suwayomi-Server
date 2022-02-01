#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

electron_version="v14.0.0"
arch=$1
if [ $arch = "win32" ]; then
  jre="OpenJDK8U-jre_x86-32_windows_hotspot_8u292b10.zip"
  jre_release="jdk8u292-b10"
  jre_url="https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/$jre_release/$jre"
  arch="windows-x86"
  electron="electron-$electron_version-win32-ia32.zip"
else
  jre="OpenJDK8U-jre_x64_windows_hotspot_8u302b08.zip"
  jre_release="jdk8u302-b08"
  jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/$jre_release/$jre"
  arch="windows-x64"
  electron="electron-$electron_version-win32-x64.zip"
fi

jre_dir="$jre_release-jre"

echo "creating windows bundle"

jar=$(ls ../server/build/*.jar | tail -n1)
jar_name=$(echo $jar | cut -d'/' -f4)
release_name=$(echo $jar_name | sed 's/.jar//')-$arch

# make release dir
mkdir $release_name

echo "Dealing with jre..."
if [ ! -f $jre ]; then
  curl -L $jre_url -o $jre
fi
unzip $jre
mv $jre_dir $release_name/jre

echo "Dealing with electron"
if [ ! -f $electron ]; then
  curl -L "https://github.com/electron/electron/releases/download/$electron_version/$electron" -o $electron
fi
unzip $electron -d $release_name/electron

# change electron's icon
rcedit="rcedit-x86.exe"
if [ ! -f $rcedit ]; then
  curl -L "https://github.com/electron/rcedit/releases/download/v1.1.1/$rcedit" -o $rcedit
fi

# check if running under github actions
if [ $CI = true ]; then
  # change electron executable's icon
  sudo dpkg --add-architecture i386
  wget -qO - https://dl.winehq.org/wine-builds/winehq.key | sudo apt-key add -
  sudo add-apt-repository ppa:cybermax-dexter/sdl2-backport
  sudo apt-add-repository "deb https://dl.winehq.org/wine-builds/ubuntu $(lsb_release -cs) main"
  sudo apt install --install-recommends winehq-stable
  sudo apt install -y wixl
fi
# this script assumes that wine is installed here on out

WINEARCH=win32 wine $rcedit $release_name/electron/electron.exe --set-icon ../server/src/main/resources/icon/faviconlogo.ico

# copy artifacts
cp $jar $release_name/Tachidesk.jar
cp "resources/Tachidesk Browser Launcher.bat" $release_name
cp "resources/Tachidesk Debug Launcher.bat" $release_name
cp "resources/Tachidesk Electron Launcher.bat" $release_name

zip_name=$release_name.zip
zip -9 -r $zip_name $release_name

# create msi package
msi_name=$release_name.msi
release_ver=$(tmp=${jar%-*} && echo ${tmp##*-} | tr -d v)
icon="../server/src/main/resources/icon/faviconlogo.ico"

find $release_name/jre | wixl-heat --var var.SourceDir -p $release_name/ --directory-ref jre --component-group jre >jre.wxs
find $release_name/electron | wixl-heat --var var.SourceDir -p $release_name/ --directory-ref electron --component-group electron >electron.wxs
if [ $arch = "win32" ]; then
  wixl -D ProductVersion=$release_ver -D SourceDir=$release_name -D Icon=$icon \
    --arch x86 resources/msi/tachidesk-server-x86.wxs jre.wxs electron.wxs -o $msi_name
else
  wixl -D ProductVersion=$release_ver -D SourceDir=$release_name -D Icon=$icon \
    --arch x64 resources/msi/tachidesk-server-x64.wxs jre.wxs electron.wxs -o $msi_name
fi

rm -rf $release_name

# clean up from possible previous runs
if [ -f ../server/build/$zip_name ]; then
  rm ../server/build/$zip_name
fi
if [ -f ../server/build/$msi_name ]; then
  rm ../server/build/$msi_name
fi

mv $zip_name ../server/build/
mv $msi_name ../server/build/
