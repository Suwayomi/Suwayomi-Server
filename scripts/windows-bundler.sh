#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

echo "Downloading packr jar..."

packr="packr-all-4.0.0.jar"
curl -L "https://github.com/libgdx/packr/releases/download/4.0.0/packr-all-4.0.0.jar" -o $packr

echo "Downloading jre..."

jre="OpenJDK8U-jre_x64_windows_hotspot_8u292b10.zip"
curl -L "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u292-b10/OpenJDK8U-jre_x64_windows_hotspot_8u292b10.zip" -o $jre

echo "creating windows bundle"

jar=$(ls ../server/build/Tachidesk-*.jar)
jar_name=$(echo $jar | cut -d'/' -f4)
release_name=$(echo $jar_name | cut -d'.' -f4 --complement)-win64

cp $jar "Tachidesk.jar"

java -jar $packr \
     --platform windows64 \
     --jdk $jre \
     --executable Tachidesk \
     --classpath Tachidesk.jar \
     --mainclass ir.armor.tachidesk.MainKt \
     --vmargs Xmx4G \
     --output $release_name

cp resources/Tachidesk-debug.bat $release_name

zip_name=$release_name.zip
zip -9 -r $zip_name $release_name

cp $zip_name ../server/build/
