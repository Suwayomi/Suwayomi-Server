#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

echo "Creating DEB Package"
pkgname="tachidesk-server"
PkgName="Tachidesk-Server"
jar=$(ls ../server/build/*.jar | tail -n1)
pkgver="$(tmp="${jar%-*}" && echo "${tmp##*-}" | tr -d v)"
pkgrel=1

srcdir="$pkgname-$pkgver"               # uses hyphen "-"
srctgz="${pkgname}_$pkgver.orig.tar.gz" # uses underscore "_"
deb="${pkgname}_$pkgver-${pkgrel}_all.deb"
Deb="${PkgName}_$pkgver-${pkgrel}_all.deb"

# Prepare
mkdir "$srcdir/"
cp "$jar" "$srcdir/$pkgname.jar"
cp "resources/$pkgname-browser-launcher.sh" "$srcdir/"
cp "resources/$pkgname-debug-launcher.sh" "$srcdir/"
cp "resources/$pkgname-electron-launcher-debian.sh" "$srcdir/$pkgname-electron-launcher.sh"
cp "resources/$pkgname.desktop" "$srcdir/"
cp "../server/src/main/resources/icon/faviconlogo.png" "$srcdir/$pkgname.png"

GZIP=-9 tar -cvzf "$srctgz" "$srcdir/"

cp -r "resources/debian" "$srcdir/"
sed -i "s/\${pkgver}/$pkgver/" "$srcdir/debian/changelog"
sed -i "s/\${pkgrel}/$pkgrel/" "$srcdir/debian/changelog"

# Build
mkdir "debuild/"
mv "$srctgz" "$srcdir/" "debuild/"
sudo apt install devscripts build-essential dh-exec
# --lintian-opts --profile are for building Debian packages on Ubuntu 
cd "debuild/$srcdir/debian"
debuild -uc -us --lintian-opts --profile debian
cd -

# clean up from possible previous runs
if [ -f "../server/build/$Deb" ]; then
    rm "../server/build/$Deb"
fi

mv "debuild/$deb" "../server/build/$Deb"
rm -rf "debuild/"
