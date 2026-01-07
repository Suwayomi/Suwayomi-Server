#!/bin/sh

set -e

url="$1"
arch="$2"
installdir="${3:-/opt/kcef/jcef}"

echo "Will try to download matching KCEF to $installdir, arch=$arch, api url=$url"

if [ -d "$installdir" ] && [ -f "$installdir/install.lock" ]; then
  echo "JCEF already downloaded to $installdir, nothing to do"
  exit 0
fi

if [ -z "$url" ]; then
  echo "Not downloading KCEF since no URL specified"
  exit 0
fi

arpath=/tmp/kcef/jcef.tar.gz
expath=/tmp/kcef/jcef
mkdir -p "$expath"

if [ ! -f "$arpath" ]; then
  body="`curl -# -H 'accept: application/vnd.github+json' "$url" | jq -r '.body'`"
  archive="`echo "$body" | gawk -F'|' '
    function compare_sdk(i1, v1, i2, v2) {
      # sort sdks last (https://github.com/DatL4g/KCEF/blob/0665269b7d6a91b0ee187f4432bb5be5ca41a112/kcef/src/main/kotlin/dev/datlag/kcef/KCEFBuilder.kt#L743-L755)
      if (v1 ~ /sdk/ && v2 ~ /sdk/) return 0;
      if (v1 ~ /sdk/) return 1;
      if (v2 ~ /sdk/) return -1;
      return 0;
    }
    BEGIN {
      # ensure urls is an array
      delete urls[0];
      # parse os/arch tuple
      match("'"$arch"'", /(.*)\/(.*)/, a);
      os=a[1];
      arch=a[2];
      if (arch == "amd64") arch="x64";
      if (arch == "arm64") arch="aarch64";
    }
    # for each line, check that the third table column contains a url and if so, extract it
    # also need to check that it contains JCEF and matches the os/arch tuple
    match($4, /(https?:\/\/|www.)[-a-zA-Z0-9+&@#\/%?=~_|!:.;]*[-a-zA-Z0-9+&@#/%=~_|]/, m) {
      # if so, push to the urls array; there is no push function, so do this cursed construction
      # arrays by convention start at 1, so do that
      if (m[0] ~ /jcef/ && m[0] ~ os && m[0] ~ arch && m[0] ~ /\.tar\.gz$/) urls[length(urls)+1] = m[0];
    }
    END {
      # now make sure sdk is sorted last, since we dont actually need the full sdk
      asort(urls, sorted, "compare_sdk");
      for (x in sorted) print sorted[x];
    }
  ' | head -n1`"

  if [ -z "$archive" ]; then
    echo "No suitable archive found on release page, so not downloading"
    exit 0
  fi

  echo "Found suitable JCEF release: $archive"
  curl -# -L -H 'accept: application/x-tar' -o "$arpath" "$archive"
fi

set -xe
tar -C "$expath" -xf "$arpath"
libfolder="`find "$expath" -type d -name lib`"

if [ -z "$libfolder" ]; then
  echo "lib folder not found in extracted archive, aborting"
  rm -rf /tmp/kcef
  exit 0
fi

mkdir -p "$installdir"
rmdir "$installdir" # we abuse -p to make sure all parent directories are created, then delete the actual target, since mv would move the libfolder inside otherwise
mv "$libfolder" "$installdir"
chmod -R a+x "$installdir"
touch "$installdir/install.lock"
rm -rf /tmp/kcef
