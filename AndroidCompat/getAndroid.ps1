# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

# This is a windows only PowerShell script to create android.jar stubs

# foolproof against running from AndroidCompat dir instead of running from project root
if ((Split-Path -Path (Get-Location) -Leaf) -eq "AndroidCompat") {
    Set-Location ..
}

Write-Output "Getting required Android.jar..."
Remove-Item -Recurse -Force "tmp" -ErrorAction SilentlyContinue | Out-Null
New-Item -ItemType Directory -Force -Path "tmp" | Out-Null

$androidEncoded = (Invoke-WebRequest -Uri "https://android.googlesource.com/platform/prebuilts/sdk/+/6cd31be5e4e25901aadf838120d71a79b46d9add/30/public/android.jar?format=TEXT" -UseBasicParsing).content

$android_jar = Join-Path (Get-Location) "tmp\android.jar"

[IO.File]::WriteAllBytes($android_jar, [Convert]::FromBase64String($androidEncoded))

# We need to remove any stub classes that we have implementations for
Write-Output "Patching JAR..."

[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression') | Out-Null

function Remove-Files-Zip($zipfile, $paths)
{
    $stream = [IO.FileStream]::new($zipfile, [IO.FileMode]::Open)
    $mode   = [IO.Compression.ZipArchiveMode]::Update
    $zip    = [IO.Compression.ZipArchive]::new($stream, $mode)

    $pathArray = @($paths)
    $entriesToDelete = [System.Collections.Generic.List[object]]::new()

    foreach ($entry in $zip.Entries) {
        foreach ($p in $pathArray) {
            if ($entry.FullName -like $p) {
                $entriesToDelete.Add($entry)
                break
            }
        }
    }

    foreach ($entry in $entriesToDelete) {
        Write-Output "Deleting: $($entry.FullName)"
        $entry.Delete()
    }

    $zip.Dispose()
    $stream.Dispose()
}

Write-Output "Removing org.json..."
Remove-Files-Zip $android_jar 'org/json/*'

Write-Output "Removing org.apache..."
Remove-Files-Zip $android_jar 'org/apache/*'

Write-Output "Removing org.w3c..."
Remove-Files-Zip $android_jar 'org/w3c/*'

Write-Output "Removing org.xml..."
Remove-Files-Zip $android_jar 'org/xml/*'

Write-Output "Removing org.xmlpull..."
Remove-Files-Zip $android_jar 'org/xmlpull/*'

Write-Output "Removing junit..."
Remove-Files-Zip $android_jar 'junit/*'

Write-Output "Removing javax..."
Remove-Files-Zip $android_jar 'javax/*'

Write-Output "Removing java..."
Remove-Files-Zip $android_jar 'java/*'

Write-Output "Removing overriden classes..."
Remove-Files-Zip $android_jar @(
    'android/app/Application.class',
    'android/app/Service.class',
    'android/net/Uri.class',
    'android/net/Uri$Builder.class',
    'android/os/Environment.class',
    'android/text/format/Formatter.class',
    'android/text/Html.class'
)

function Dedupe($path)
{
    Push-Location $path
    $root = (Get-Location).Path
    $classLookup = @{}

    Get-ChildItem . -File -Recurse | ForEach-Object {
        $rel  = $_.FullName.Substring($root.Length).TrimStart('\', '/')
        $rel  = $rel -replace '\\', '/'
        $name = $rel -replace '\.[^.]+$', ''

        "Processing class: $name"
        $classLookup[$name] = $true
    }

    $stream = [IO.FileStream]::new($android_jar, [IO.FileMode]::Open)
    $mode   = [IO.Compression.ZipArchiveMode]::Update
    $zip    = [IO.Compression.ZipArchive]::new($stream, $mode)

    try {
        $entriesToDelete = [System.Collections.Generic.List[object]]::new()

        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notlike '*.class') { continue }

            # Matching patterns:
            # "$name.class"
            # "$name`$*.class"
            # "${name}Kt.class"
            # "${name}Kt`$*.class"

            $name       = $entry.FullName -replace '\.class$', ''
            $name       = $name -replace '\$.*$', ''
            $kotlinName = $name -replace 'Kt$', ''

            if ($classLookup.ContainsKey($name) -or
                ($name -ne $kotlinName -and $classLookup.ContainsKey($kotlinName))) {
                $entriesToDelete.Add($entry)
            }
        }

        foreach ($entry in $entriesToDelete) {
            $entry.Delete()
        }
    }
    finally {
        $zip.Dispose()
        $stream.Dispose()
        Pop-Location
    }
}

Dedupe "AndroidCompat/src/main/java"
Dedupe "server/src/main/kotlin"

Write-Output "Copying Android.jar to library folder..."
New-Item -ItemType Directory -Force -Path "AndroidCompat/lib" | Out-Null
Move-Item -Force $android_jar "AndroidCompat/lib/android.jar"

Write-Output "Cleaning up..."
Remove-Item -Recurse -Force "tmp"

Write-Output "Done!"
