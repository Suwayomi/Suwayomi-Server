# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

# This is a windows only PowerShell script to create android.jar stubs

# foolproof against running from AndroidCompat dir instead of running from project root
if ($(Split-Path -Path (Get-Location) -Leaf) -eq "AndroidCompat" ) {
    Set-Location ..
}

Write-Output "Getting required Android.jar..."
Remove-Item -Recurse -Force "tmp" -ErrorAction SilentlyContinue | Out-Null
New-Item -ItemType Directory -Force -Path "tmp" | Out-Null

$androidEncoded = (Invoke-WebRequest -Uri "https://android.googlesource.com/platform/prebuilts/sdk/+/3b8a524d25fa6c3d795afb1eece3f24870c60988/27/public/android.jar?format=TEXT" -UseBasicParsing).content

$android_jar = (Get-Location).Path + "\tmp\android.jar"

[IO.File]::WriteAllBytes($android_jar, [Convert]::FromBase64String($androidEncoded))

# We need to remove any stub classes that we have implementations for
Write-Output "Patching JAR..."

function Remove-Files-Zip($zipfile, $path)
{
    [Reflection.Assembly]::LoadWithPartialName('System.IO.Compression') | Out-Null

    $stream = New-Object IO.FileStream($zipfile, [IO.FileMode]::Open)
    $mode   = [IO.Compression.ZipArchiveMode]::Update
    $zip    = New-Object IO.Compression.ZipArchive($stream, $mode)

    ($zip.Entries | Where-Object { $_.FullName -like $path }) | ForEach-Object { Write-Output "Deleting: $($_.FullName)"; $_.Delete() }

    $zip.Dispose()
    $stream.Close()
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
Remove-Files-Zip $android_jar 'android/app/Application.class'
Remove-Files-Zip $android_jar 'android/app/Service.class'
Remove-Files-Zip $android_jar 'android/net/Uri.class'
Remove-Files-Zip $android_jar 'android/net/Uri$Builder.class'
Remove-Files-Zip $android_jar 'android/os/Environment.class'
Remove-Files-Zip $android_jar 'android/text/format/Formatter.class'
Remove-Files-Zip $android_jar 'android/text/Html.class'

function Dedupe($path)
{
    Push-Location $path
    $classes = Get-ChildItem . *.* -Recurse | Where-Object { !$_.PSIsContainer }
    $classes | ForEach-Object {
        "Processing class: $($_.FullName)"
        Remove-Files-Zip $android_jar "$($_.Name).class" | Out-Null
        Remove-Files-Zip $android_jar "$($_.Name)$*.class" | Out-Null
        Remove-Files-Zip $android_jar "$($_.Name)Kt.class" | Out-Null
        Remove-Files-Zip $android_jar "$($_.Name)Kt$*.class" | Out-Null
    }
    Pop-Location
}

Dedupe "AndroidCompat/src/main/java"
Dedupe "server/src/main/kotlin"

Write-Output "Copying Android.jar to library folder..."
Move-Item -Force $android_jar "AndroidCompat/lib/android.jar"

Write-Output "Cleaning up..."
Remove-Item -Recurse -Force "tmp"

Write-Output "Done!"
