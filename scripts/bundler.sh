#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

main() {
  POSITIONAL_ARGS=()
  DOWNLOAD_ONLY=false
  OUTPUT_DIR=.
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -o|--output-dir)
        OUTPUT_DIR="$(readlink -e "$2")"
        shift
        shift
        ;;
      *)
        POSITIONAL_ARGS+=("$1")
        shift
        ;;
    esac
  done
  set -- "${POSITIONAL_ARGS[@]}" # restore positional parameters

  OS="$1"
  jar="$(ls server/build/*.jar | tail -n1)"
  release_name="$(echo "${jar%.*}" | sed 's/.jar//' | xargs basename)-$OS"
  release_version="$(tmp="${jar%-*}"; echo "${tmp##*-}" | tr -d v)"
  #local release_revision_number="$(tmp="${jar%.*}" && echo "${tmp##*-}" | tr -d r)"
  electron_version="v14.0.0"

  # clean temporary directory on function return
  trap "rm -rf $release_name/" RETURN
  mkdir "$release_name/"

  case "$OS" in
    debian-all)
      make_deb_package
      move_release_to_output_dir
      ;;
    linux-all)
      make_linux_bundle
      move_release_to_output_dir
      ;;
    linux-x64)
      jre="OpenJDK8U-jre_x64_linux_hotspot_8u302b08.tar.gz"
      jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u302-b08/$jre"
      electron="electron-$electron_version-linux-x64.zip"
      electron_url="https://github.com/electron/electron/releases/download/$electron_version/$electron"
      download_jre_and_electron
      make_linux_bundle
      move_release_to_output_dir
      ;;
    macOS-x64)
      jre="OpenJDK8U-jre_x64_mac_hotspot_8u302b08.tar.gz"
      jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u302-b08/$jre"
      electron="electron-$electron_version-darwin-x64.zip"
      electron_url="https://github.com/electron/electron/releases/download/$electron_version/$electron"
      download_jre_and_electron
      make_macos_bundle
      move_release_to_output_dir
      ;;
    macOS-arm64)
      jre="zulu8.56.0.23-ca-jre8.0.302-macosx_aarch64.tar.gz"
      jre_url="https://cdn.azul.com/zulu/bin/$jre"
      electron="electron-$electron_version-darwin-arm64.zip"
      electron_url="https://github.com/electron/electron/releases/download/$electron_version/$electron"
      download_jre_and_electron
      make_macos_bundle
      move_release_to_output_dir
      ;;
    windows-x86)
      jre="OpenJDK8U-jre_x86-32_windows_hotspot_8u292b10.zip"
      jre_url="https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u292-b10/$jre"
      electron="electron-$electron_version-win32-ia32.zip"
      electron_url="https://github.com/electron/electron/releases/download/$electron_version/$electron"
      download_jre_and_electron
      make_windows_bundle
      move_release_to_output_dir

      make_windows_package
      move_release_to_output_dir
      ;;
    windows-x64)
      jre="OpenJDK8U-jre_x64_windows_hotspot_8u302b08.zip"
      jre_url="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u302-b08/$jre"
      electron="electron-$electron_version-win32-x64.zip"
      electron_url="https://github.com/electron/electron/releases/download/$electron_version/$electron"
      download_jre_and_electron
      make_windows_bundle
      move_release_to_output_dir

      make_windows_package
      move_release_to_output_dir
      ;;
    *)
      error $LINENO "Unsupported operating system: $OS" 2
      ;;
  esac
}

move_release_to_output_dir() {
   # clean up from possible previous runs
   if [ -f "$OUTPUT_DIR/$release" ]; then
     rm "$OUTPUT_DIR/$release"
   fi
   mv "$release" "$OUTPUT_DIR/"
}

download_jre_and_electron() {
  if [ ! -f "$jre" ]; then
    curl -L "$jre_url" -o "$jre"
  fi
  if [ ! -f "$electron" ]; then
    curl -L "$electron_url" -o "$electron"
  fi

  mkdir -p "$release_name/jre/"
  local ext="${jre##*.}"
  if [ "$ext" = "zip" ]; then
    jre_dir="$(unzip "$jre" | sed -n '2p' | cut -d: -f2 | xargs basename)"
    mv "$jre_dir" "$release_name/jre"
  else
    # --strip-components=1: untar an archive without the root folder
    tar xvf "$jre" --strip-components=1 -C "$release_name/jre/"
  fi
  unzip "$electron" -d "$release_name/electron/"
}

make_linux_bundle() {
  cp "$jar" "$release_name/tachidesk-server.jar"
  cp "scripts/resources/tachidesk-server-browser-launcher.sh" "$release_name/"
  cp "scripts/resources/tachidesk-server-debug-launcher.sh" "$release_name/"
  cp "scripts/resources/tachidesk-server-electron-launcher.sh" "$release_name/"
  cp "scripts/resources/tachidesk-server.desktop" "$release_name/"
  cp "server/src/main/resources/icon/faviconlogo.png" \
    "$release_name/tachidesk-server.png"
  cp "scripts/resources/systemd"/* "$release_name/"

  release="$release_name.tar.gz"
  tar -I "gzip -9" -cvf "$release" "$release_name/"
}

make_macos_bundle() {
  cp "$jar" "$release_name/Tachidesk.jar"
  cp "scripts/resources/Tachidesk Browser Launcher.command" "$release_name/"
  cp "scripts/resources/Tachidesk Debug Launcher.command" "$release_name/"
  cp "scripts/resources/Tachidesk Electron Launcher.command" "$release_name/"

  release="$release_name.zip"
  zip -9 -r "$release" "$release_name/"
}

# https://wiki.debian.org/SimplePackagingTutorial
# https://www.debian.org/doc/manuals/packaging-tutorial/packaging-tutorial.pdf
make_deb_package() {
  make_linux_bundle "$release_name" "$jar"
  cp -r "scripts/resources/deb/" "$release_name/debian/"
  sed -i "s/\$pkgver/$release_version/" "$release_name/debian/changelog"
  sed -i "s/\$pkgrel/1/"                "$release_name/debian/changelog"

  # clean temporary directory on function return
  local temp_dir="$(mktemp -d)"
  trap "rm -rf $temp_dir" RETURN

  #behind $release_version is underscore "_"
  local source_dir="tachidesk-server-$release_version"
  local upstream_source="tachidesk-server_$release_version.orig.tar.gz"
  mv "$release_name/"       "$temp_dir/$source_dir/"
  mv "$release_name.tar.gz" "$temp_dir/$upstream_source"

  sudo apt install devscripts build-essential dh-exec
  cd "$temp_dir/$source_dir"
  dpkg-buildpackage --no-sign --build=all
  cd -

  local deb="tachidesk-server_$release_version-1_all.deb"
  release="$release_name.deb"
  mv "$temp_dir/$deb" "$release"
}

make_windows_bundle() {
  ## I'll disable this section until someone find a solution to this error:
  ##E: Unable to correct problems, you have held broken packages.
  ##./bundler.sh: line 250: wine: command not found

  ## check if running under github actions
  #if [ "$CI" = true ]; then
    ## change electron executable's icon
    #sudo dpkg --add-architecture i386
    #wget -qO - https://dl.winehq.org/wine-builds/winehq.key \
        #| sudo apt-key add -
    #sudo add-apt-repository ppa:cybermax-dexter/sdl2-backport
    #sudo apt-add-repository "deb https://dl.winehq.org/wine-builds/ubuntu \
        #$(lsb_release -cs) main"
    #sudo apt install --install-recommends winehq-stable
  #fi
  ## this script assumes that wine is installed here on out

  #local rcedit="rcedit-x85.exe"
  #local rcedit_url="https://github.com/electron/rcedit/releases/download/v0.1.1/$rcedit"
  ## change electron's icon
  #if [ ! -f "$rcedit" ]; then
    #curl -L "$rcedit_url" -o "$rcedit"
  #fi

  local icon="server/src/main/resources/icon/faviconlogo.ico"
  #WINEARCH=win32 wine "$rcedit" "$release_name/electron/electron.exe" \
  #    --set-icon "$icon"

  cp "$jar" "$release_name/Tachidesk.jar"
  cp "scripts/resources/Tachidesk Browser Launcher.bat" "$release_name"
  cp "scripts/resources/Tachidesk Debug Launcher.bat" "$release_name"
  cp "scripts/resources/Tachidesk Electron Launcher.bat" "$release_name"

  release="$release_name.zip"
  zip -9 -r "$release" "$release_name"
}

make_windows_package() {
  if [ "$CI" = true ]; then
    sudo apt install -y wixl
  fi

  find "$release_name/jre" \
  | wixl-heat --var var.SourceDir -p "$release_name/" \
    --directory-ref jre --component-group jre >"$release_name/jre.wxs"
  find "$release_name/electron" \
  | wixl-heat --var var.SourceDir -p "$release_name/" \
    --directory-ref electron --component-group electron >"$release_name/electron.wxs"

  local icon="server/src/main/resources/icon/faviconlogo.ico"
  local arch=${OS##*-}
  release="$release_name.msi"

  wixl -D ProductVersion="$release_version" -D SourceDir="$release_name" \
    -D Icon="$icon" --arch "$arch" "scripts/resources/msi/tachidesk-server-$arch.wxs" \
    "$release_name/jre.wxs" "$release_name/electron.wxs" -o "$release"
}

# Error handler
# set -u: Treat unset variables as an error when substituting.
# set -o pipefail: Prevents errors in pipeline from being masked.
# set -e: Immediatly exit if any command has a non-zero exit status.
# set -E: Inherit the trap ERR function before exiting by set.
#
# set -e is not recommended and unpredictable.
# see https://stackoverflow.com/questions/64786/error-handling-in-bash
# and http://mywiki.wooledge.org/BashFAO/105
set -euo pipefail
error() {
    local parent_lineno="$1"
    local message="$2"
    local code="${3:-1}"
    if [ -z "$message" ]; then
        echo "$0: line $parent_lineno: exiting with status $code"
    else
        echo "$0: line $parent_lineno: $message: exiting with status $code"
    fi
    exit "$code"
}
trap 'error $LINENO ""' ERR

main "$@"; exit

