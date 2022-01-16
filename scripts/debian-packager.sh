#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

echo "creating debian package"
jar=$(ls ../server/build/*.jar | tail -n1)
release_ver=$(tmp="${jar%-*}" && echo "${tmp##*-}" | tr -d v)
orig_dir="tachidesk-$release_ver"                # dir uses hyphen "-"
orig_tar_gz="tachidesk_$release_ver.orig.tar.gz" # orig file uses underscore "_"
package_name="tachidesk_$release_ver-1_all.deb"

# copy artifacts
mkdir "$orig_dir"
cp "$jar" "$orig_dir/Tachidesk.jar"
cp -r "resources/debian" "$orig_dir"
cp "resources/tachidesk-browser-launcher-aur.sh" "$orig_dir/debian"
cp "resources/tachidesk-debug-launcher-aur.sh" "$orig_dir/debian"
cp "resources/tachidesk-electron-launcher-aur.sh" "$orig_dir/debian"
cp "resources/tachidesk.desktop" "$orig_dir/debian"
cp "../server/src/main/resources/icon/faviconlogo.png" "$orig_dir/debian"

# prepare
tar cvzf "$orig_tar_gz" "$orig_dir/Tachidesk.jar"
sed -i "s/\${version}/$release_ver/" "$orig_dir/debian/changelog"

# build
mkdir "build"
mv $orig_dir $orig_tar_gz "build/"
cd "build/$orig_dir/debian"
sudo apt install devscripts build-essential dh-exec
# --lintian-opts --profile debian: build Debian packages on Ubuntu
debuild -uc -us --lintian-opts --profile debian
cd -

# clean build directory
mv "build/$package_name" "./"
rm -rf "build"

# clean up from possible previous runs
if [ -f "../server/build/$package_name" ]; then
  rm "../server/build/$package_name"
fi

mv "$package_name" "../server/build/"
