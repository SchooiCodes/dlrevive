#!/bin/sh
# Build the Manager-compatible patch bundle and stage the release descriptor.
# Usage:  sh scripts/release.sh <version>
# Example: sh scripts/release.sh 1.0.0
#
# Produces:
#   build/release/patches-<version>.rvp   (rename of the built Manager patch jar)
#   manager.json                          (stable custom-source descriptor)
# The existing jar output at patches/build/libs/dlvrevive-patches-manager.jar is unchanged.

set -e
VERSION="${1:-1.0.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Building patch bundle..."
rm -rf integrations/build patches/build
./gradlew :patches:jar --offline --console=plain

mkdir -p build/release
JAR="patches/build/libs/dlvrevive-patches-manager.jar"
RVP="build/release/patches-${VERSION}.rvp"
cp "$JAR" "$RVP"
echo "Wrote $RVP"

# The descriptor is kept in the repo at a stable path. Update its version/date/download_url
# when cutting a new release, then commit and push before creating the GitHub Release.
cat > manager.json <<EOF
{
  "version": "${VERSION}",
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%S)",
  "description": "dlrevive: restore YouTube Music song downloads without Premium using in-app audio resolution. Requires the GmsCore support patch from ReVanced Patches and ReVanced GmsCore (microG) on the device.",
  "download_url": "https://github.com/SchooiCodes/dlrevive/releases/download/v${VERSION}/patches-${VERSION}.rvp",
  "signature_download_url": null
}
EOF
echo "Wrote manager.json (commit and push it, then attach $RVP to a v${VERSION} GitHub Release)."
