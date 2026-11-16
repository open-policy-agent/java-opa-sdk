#!/usr/bin/env bash
# Fails unless a release tag matches the version in gradle.properties.
#
# Usage: tools/check-release-version.sh <tag>   (e.g. v0.7.0)
#
# Maven Central artifacts get their version from gradle.properties, not from the tag, so a tag cut
# without the version bump would publish the previous version. Run this before anything is
# published.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <tag>" >&2
  exit 2
fi

tag="$1"
properties="$(dirname "$0")/../gradle.properties"
version="$(sed -n 's/^version[[:space:]]*=[[:space:]]*//p' "$properties" | tr -d '[:space:]')"

if [ -z "$version" ]; then
  echo "error: no version in gradle.properties" >&2
  exit 1
fi

if [ "${tag#v}" != "$version" ]; then
  echo "error: tag ${tag} does not match version ${version} in gradle.properties" >&2
  exit 1
fi
