#!/bin/bash

# Copyright (C) Contributors to the Suwayomi project
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

main() {
  POSITIONAL_ARGS=()
  OUTPUT_DIR=.
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -o|--output-dir)
        OUTPUT_DIR="$(readlink -e "$2" || exit 1)"
        shift
        shift
        ;;
      *)
        POSITIONAL_ARGS+=("$1")
        shift
        ;;
    esac
  done
  # restore positional parameters
  set -- "${POSITIONAL_ARGS[@]}"

  OS="$1"
  JAR="$(ls server/build/*.jar | tail -n1)"
  RELEASE_NAME="$(echo "${JAR%.*}" | xargs basename)-$OS"
  RELEASE_VERSION=$(echo "$JAR" | grep -oP "v\K[0-9]+\.[0-9]+\.[0-9]+")
  #RELEASE_REVISION_NUMBER="$(tmp="${JAR%.*}" && echo "${tmp##*-}" | tr -d r)"
  local electron_version="v37.2.5"

  # clean temporary directory on function return
  trap "rm -rf $RELEASE_NAME/" RETURN
  mkdir "$RELEASE_NAME/"

  download_launcher

  if [ ! -f scripts/resources/catch_abort.so ]; then
    gcc -fPIC -shared scripts/resources/catch_abort.c -lpthread -o scripts/resources/catch_abort.so
  fi

  JRE_ZULU="25.30.17_25.0.1"
  JRE_RELEASE="jre${JRE_ZULU#*_}" # e.g. jre25.0.1
  ZULU_RELEASE="zulu${JRE_ZULU%_*}" # e.g. zulu25.30.17

  case "$OS" in
    debian-all)
      RELEASE="$RELEASE_NAME.deb"
      download_jogamp "linux-*" # it's easier to bundle them ourselves than to handle Debian's path conventions
      make_deb_package
      move_release_to_output_dir
      ;;
    redhat-x64)
      RELEASE="$RELEASE_NAME.rpm"
      make_rpm_package
      move_release_to_output_dir
      ;;
    appimage)
      JRE="$ZULU_RELEASE-ca-$JRE_RELEASE-linux_x64.zip"
      JRE_DIR="${JRE%.*}"
      JRE_URL="https://cdn.azul.com/zulu/bin/$JRE"
      download_jogamp "linux-amd64"
      setup_jre

      RELEASE="$RELEASE_NAME.AppImage"
      make_appimage
      move_release_to_output_dir
      ;;
    linux-assets)
      RELEASE="$RELEASE_NAME.tar.gz"
      copy_linux_package_assets_to "$RELEASE_NAME/"
      tar -I "gzip -9" -cvf "$RELEASE" "$RELEASE_NAME/"
      move_release_to_output_dir
      ;;
    linux-x64)
      JRE="$ZULU_RELEASE-ca-$JRE_RELEASE-linux_x64.zip"
      JRE_DIR="${JRE%.*}"
      JRE_URL="https://cdn.azul.com/zulu/bin/$JRE"
      ELECTRON="electron-$electron_version-linux-x64.zip"
      ELECTRON_URL="https://github.com/electron/electron/releases/download/$electron_version/$ELECTRON"
      download_electron
      download_jogamp "linux-amd64"
      setup_jre
      tree "$RELEASE_NAME"

      RELEASE="$RELEASE_NAME.tar.gz"
      make_linux_bundle
      move_release_to_output_dir
      ;;
    macOS-x64)
      JRE="$ZULU_RELEASE-ca-$JRE_RELEASE-macosx_x64.zip"
      JRE_DIR="${JRE%.*}"
      JRE_URL="https://cdn.azul.com/zulu/bin/$JRE"
      ELECTRON="electron-$electron_version-darwin-x64.zip"
      ELECTRON_URL="https://github.com/electron/electron/releases/download/$electron_version/$ELECTRON"
      download_electron
      download_jogamp "macosx-universal"
      setup_jre
      tree "$RELEASE_NAME"

      RELEASE="$RELEASE_NAME.tar.gz"
      make_macos_bundle
      move_release_to_output_dir
      ;;
    macOS-arm64)
      JRE="$ZULU_RELEASE-ca-$JRE_RELEASE-macosx_aarch64.zip"
      JRE_DIR="${JRE%.*}"
      JRE_URL="https://cdn.azul.com/zulu/bin/$JRE"
      ELECTRON="electron-$electron_version-darwin-arm64.zip"
      ELECTRON_URL="https://github.com/electron/electron/releases/download/$electron_version/$ELECTRON"
      download_electron
      download_jogamp "macosx-universal"
      setup_jre
      tree "$RELEASE_NAME"

      RELEASE="$RELEASE_NAME.tar.gz"
      make_macos_bundle
      move_release_to_output_dir
      ;;
    windows-x64)
      JRE="$ZULU_RELEASE-ca-$JRE_RELEASE-win_x64.zip"
      JRE_DIR="${JRE%.*}"
      JRE_URL="https://cdn.azul.com/zulu/bin/$JRE"
      ELECTRON="electron-$electron_version-win32-x64.zip"
      ELECTRON_URL="https://github.com/electron/electron/releases/download/$electron_version/$ELECTRON"
      download_electron
      download_jogamp "windows-amd64"
      setup_jre
      tree "$RELEASE_NAME"

      RELEASE="$RELEASE_NAME.zip"
      make_windows_bundle
      move_release_to_output_dir

      RELEASE="$RELEASE_NAME.msi"
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
   if [ -f "$OUTPUT_DIR/$RELEASE" ]; then
     rm "$OUTPUT_DIR/$RELEASE"
   fi
   mv "$RELEASE" "$OUTPUT_DIR/"
}

download_launcher() {
  LAUNCHER_URL=$(curl -s "https://api.github.com/repos/Suwayomi/Suwayomi-Launcher/releases/latest" | grep "browser_download_url" | grep ".jar" | head -n 1 | cut -d '"' -f 4)
  curl -L "$LAUNCHER_URL" -o "Suwayomi-Launcher.jar"
  mv "Suwayomi-Launcher.jar" "$RELEASE_NAME/Suwayomi-Launcher.jar"
}

download_jogamp() {
  local platform="$1"
  if [ ! -f jogamp-all-platforms.7z ]; then
    curl "https://jogamp.org/deployment/jogamp-current/archive/jogamp-all-platforms.7z" -o jogamp-all-platforms.7z
  fi

  7z x jogamp-all-platforms.7z "jogamp-all-platforms/lib/$platform/"
  mkdir -p "$RELEASE_NAME/natives/"
  mv jogamp-all-platforms/lib/* "$RELEASE_NAME/natives/"
  rm -rf jogamp-all-platforms
}

download_electron() {
  if [ ! -f "$ELECTRON" ]; then
    curl -L "$ELECTRON_URL" -o "$ELECTRON"
  fi

  unzip "$ELECTRON" -d "$RELEASE_NAME/electron/"
}

setup_jre() {
  if [ -d "jre" ]; then
    chmod +x ./jre/bin/java
    chmod +x ./jre/lib/jspawnhelper
    mv "jre" "$RELEASE_NAME/jre"
  else
    if [ ! -f "$JRE" ]; then
      curl -L "$JRE_URL" -o "$JRE"
    fi

    local ext="${JRE##*.}"
    if [ "$ext" = "zip" ]; then
      unzip "$JRE"
    else
      tar xvf "$JRE"
    fi
    mv "$JRE_DIR" "$RELEASE_NAME/jre"
  fi
}

copy_linux_package_assets_to() {
  local output_dir
  output_dir="$(readlink -e "$1" || exit 1)"

  cp "scripts/resources/pkg/suwayomi-server.sh" "$output_dir/"
  cp "scripts/resources/pkg/suwayomi-server.desktop" "$output_dir/"
  cp "scripts/resources/pkg/suwayomi-launcher.sh" "$output_dir/"
  cp "scripts/resources/pkg/suwayomi-launcher.desktop" "$output_dir/"
  cp "scripts/resources/pkg/systemd"/* "$output_dir/"
  cp "server/src/main/resources/icon/faviconlogo-128.png" \
    "$output_dir/suwayomi-server.png"
}

make_linux_bundle() {
  mkdir "$RELEASE_NAME/bin"
  cp "$JAR" "$RELEASE_NAME/bin/Suwayomi-Server.jar"
  cp "scripts/resources/suwayomi-launcher.sh" "$RELEASE_NAME/"
  cp "scripts/resources/suwayomi-server.sh" "$RELEASE_NAME/"
  cp "scripts/resources/catch_abort.so" "$RELEASE_NAME/bin/"

  tar -I "gzip -9" -cvf "$RELEASE" "$RELEASE_NAME/"
}

make_macos_bundle() {
  mkdir "$RELEASE_NAME/bin"
  cp "$JAR" "$RELEASE_NAME/bin/Suwayomi-Server.jar"
  cp "scripts/resources/Suwayomi Launcher.command" "$RELEASE_NAME/"

  tar -I "gzip -9" -cvf "$RELEASE" "$RELEASE_NAME/"
}

# https://wiki.debian.org/SimplePackagingTutorial
# https://www.debian.org/doc/manuals/packaging-tutorial/packaging-tutorial.pdf
make_deb_package() {
  #behind $RELEASE_VERSION is hyphen "-"
  local source_dir="suwayomi-server-$RELEASE_VERSION"
  #behind $RELEASE_VERSION is underscore "_"
  local upstream_source="suwayomi-server_$RELEASE_VERSION.orig.tar.gz"

  mkdir "$RELEASE_NAME/$source_dir/"
  mv "$RELEASE_NAME/natives" "$RELEASE_NAME/$source_dir/natives"
  mv "$RELEASE_NAME/Suwayomi-Launcher.jar" "$RELEASE_NAME/$source_dir/Suwayomi-Launcher.jar"
  cp "$JAR" "$RELEASE_NAME/$source_dir/Suwayomi-Server.jar"
  copy_linux_package_assets_to "$RELEASE_NAME/$source_dir/"
  cp "scripts/resources/catch_abort.so" "$RELEASE_NAME/$source_dir/"
  tar -I "gzip" -C "$RELEASE_NAME/" -cvf "$upstream_source" "$source_dir"

  cp -r "scripts/resources/deb/" "$RELEASE_NAME/$source_dir/debian/"
  sed -i "s/\$pkgver/$RELEASE_VERSION/" "$RELEASE_NAME/$source_dir/debian/changelog"
  sed -i "s/\$pkgrel/1/"                "$RELEASE_NAME/$source_dir/debian/changelog"

  if [ "${CI:-}" = true ]; then
    sudo apt update
    sudo apt install devscripts build-essential dh-exec
  fi
  cd "$RELEASE_NAME/$source_dir/"
  dpkg-buildpackage --no-sign --build=all
  cd -

  local deb="suwayomi-server_$RELEASE_VERSION-1_all.deb"
  mv "$RELEASE_NAME/$deb" "$RELEASE"
}

# https://www.redhat.com/en/blog/create-rpm-package
# https://rpm-packaging-guide.github.io/#preparing-software-for-packaging
make_rpm_package() {
  #behind $RELEASE_VERSION is hyphen "-"
  local source_dir="suwayomi-server-$RELEASE_VERSION"
  #behind $RELEASE_VERSION is underscore "_"
  local upstream_source="suwayomi-server_$RELEASE_VERSION.orig.tar.gz"

  mkdir "$RELEASE_NAME/$source_dir/"
  mkdir -p $RELEASE_NAME/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}

  mv "$RELEASE_NAME/Suwayomi-Launcher.jar" "$RELEASE_NAME/$source_dir/Suwayomi-Launcher.jar"
  cp "$JAR" "$RELEASE_NAME/$source_dir/Suwayomi-Server.jar"
  copy_linux_package_assets_to "$RELEASE_NAME/$source_dir/"
  # rpmbuild sees all *.so files as arch-dependent
  cp "scripts/resources/catch_abort.so" "$RELEASE_NAME/$source_dir/"
  tar -I "gzip" -C "$RELEASE_NAME/" -cvf "$upstream_source" "$source_dir"
  mv "$upstream_source" "$RELEASE_NAME/SOURCES/$upstream_source" 

  cp "scripts/resources/rpm/suwayomi-server.spec" "$RELEASE_NAME/SPECS/suwayomi-server.spec"
  sed -i "s/\$pkgver/$RELEASE_VERSION/" "$RELEASE_NAME/SPECS/suwayomi-server.spec"
  sed -i "s/\$pkgrel/1/"                "$RELEASE_NAME/SPECS/suwayomi-server.spec"

  sudo dnf update
  sudo dnf install rpmdevtools
  cd "$RELEASE_NAME/"
  rpmbuild --define "_topdir `pwd`" -bb SPECS/suwayomi-server.spec
  cd -

  local rpm="suwayomi-server-$RELEASE_VERSION-1.x86_64.rpm"
  mv "$RELEASE_NAME/RPMS/x86_64/$rpm" "$RELEASE"
}

# https://linuxconfig.org/building-a-hello-world-appimage-on-linux
make_appimage() {
  local APPIMAGE_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
  local APPIMAGE_TOOLNAME="appimagetool-x86_64.AppImage"
  mkdir "$RELEASE_NAME/bin/"
  cp "$JAR" "$RELEASE_NAME/bin/Suwayomi-Server.jar"

  cp "scripts/resources/pkg/suwayomi-server.desktop" "$RELEASE_NAME/suwayomi-server.desktop"
  cp "server/src/main/resources/icon/faviconlogo.png" "$RELEASE_NAME/suwayomi-server.png"
  cp "scripts/resources/appimage/AppRun" "$RELEASE_NAME/AppRun"
  chmod +x "$RELEASE_NAME/AppRun"

  if [ "${CI:-}" = true ]; then
    sudo apt update
    sudo apt install libfuse2
  fi
  curl -L $APPIMAGE_URL -o $APPIMAGE_TOOLNAME
  chmod +x $APPIMAGE_TOOLNAME
  ARCH=x86_64 ./$APPIMAGE_TOOLNAME "$RELEASE_NAME" "$RELEASE"
}

make_windows_bundle() {
  ## I disabled this section until someone find a solution to this error:
  ##E: Unable to correct problems, you have held broken packages.
  ##./bundler.sh: line 250: wine: command not found

  ## check if running under github actions
  #if [ "${CI:-}" = true ]; then
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

  #local icon="server/src/main/resources/icon/faviconlogo.ico"
  #WINEARCH=win32 wine "$rcedit" "$RELEASE_NAME/electron/electron.exe" \
  #    --set-icon "$icon"

  mkdir "$RELEASE_NAME/bin"
  cp "$JAR" "$RELEASE_NAME/bin/Suwayomi-Server.jar"
  cp "scripts/resources/Suwayomi Launcher.bat" "$RELEASE_NAME"

  zip -9 -r "$RELEASE" "$RELEASE_NAME"
}

make_windows_package() {
  if [ "${CI:-}" = true ]; then
    sudo apt update
    sudo apt install -y wixl
  fi

  find "$RELEASE_NAME/jre" \
  | wixl-heat --var var.SourceDir -p "$RELEASE_NAME/" \
    --directory-ref jre --component-group jre >"$RELEASE_NAME/jre.wxs"
  find "$RELEASE_NAME/electron" \
  | wixl-heat --var var.SourceDir -p "$RELEASE_NAME/" \
    --directory-ref electron --component-group electron >"$RELEASE_NAME/electron.wxs"
  find "$RELEASE_NAME/natives" \
  | wixl-heat --var var.SourceDir -p "$RELEASE_NAME/" \
    --directory-ref natives --component-group natives >"$RELEASE_NAME/natives.wxs"

  find "$RELEASE_NAME/bin" \
  | wixl-heat --var var.SourceDir -p "$RELEASE_NAME/" \
    --directory-ref bin --component-group bin >"$RELEASE_NAME/bin.wxs"

  local icon="server/src/main/resources/icon/faviconlogo.ico"
  local arch=${OS##*-}

  wixl -D ProductVersion="$RELEASE_VERSION" -D SourceDir="$RELEASE_NAME" \
    -D Icon="$icon" --arch "$arch" "scripts/resources/msi/suwayomi-server-$arch.wxs" \
    "$RELEASE_NAME/jre.wxs" "$RELEASE_NAME/electron.wxs" "$RELEASE_NAME/natives.wxs" "$RELEASE_NAME/bin.wxs" -o "$RELEASE"
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
set -uo pipefail
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


