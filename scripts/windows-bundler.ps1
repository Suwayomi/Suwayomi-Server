# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

Write-Output "Downloading jre..."

$jre="OpenJDK8U-jre_x64_windows_hotspot_8u292b10.zip"
if (!(Test-Path $jre)) {
  Invoke-WebRequest -Uri "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u292-b10/OpenJDK8U-jre_x64_windows_hotspot_8u292b10.zip" -OutFile $jre -UseBasicParsing
}

Write-Output "creating windows bundle"

$jar=$(Get-ChildItem ../server/build/Tachidesk-*.jar)
$release_name=$jar.BaseName + "-win64"

# make release dir
New-Item -ItemType Directory $release_name

Expand-Archive $jre -DestinationPath "./" -ErrorAction SilentlyContinue

# move jre
Move-Item "jdk8u292-b10-jre" "$release_name/jre"

Copy-Item $jar.FullName "$release_name/Tachidesk.jar"

Copy-Item "resources/Tachidesk Launcher.exe" $release_name
Copy-Item "resources/Tachidesk Launcher.bat" $release_name
Copy-Item "resources/Tachidesk Debug Launcher.bat" $release_name

$zip_name="$release_name.zip"
Compress-Archive -CompressionLevel Optimal -DestinationPath $zip_name -Path $release_name -Force -ErrorAction SilentlyContinue

Remove-Item -Force -Recurse $release_name

Move-Item $zip_name "../server/build/" -ErrorAction SilentlyContinue
