#!/usr/bin/env bash
# Prints the CHANGELOG.md section for a release as GitHub release notes.
#
# Usage: tools/release-notes.sh <version>   (e.g. 0.5.0 or v0.5.0)
#
# The section is everything between "## <version>" and the next "## " heading. CHANGELOG.md wraps
# prose and bullets at ~80 columns, but GitHub renders single newlines in release notes as line
# breaks, so wrapped lines are joined back onto the paragraph or bullet they continue. Nested
# bullets ("    - ") and headings start a new line. Exits non-zero if the section is missing or
# empty, so a tag cut without its changelog entry fails instead of publishing blank notes.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <version>" >&2
  exit 2
fi

version="${1#v}"
changelog="$(dirname "$0")/../CHANGELOG.md"

notes="$(awk -v heading="## ${version}" '
  $0 == heading { found = 1; next }
  found && /^## / { exit }
  found {
    line = $0
    if (line != "" && prev != "" && line !~ /^ *- / && line !~ /^#/ && prev !~ /^#/) {
      sub(/^ +/, "", line)
      prev = prev " " line
      next
    }
    if (started) print prev
    prev = line
    started = 1
  }
  END { if (started) print prev }
' "$changelog" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')"

if [ -z "$notes" ]; then
  echo "error: no \"## ${version}\" section in CHANGELOG.md" >&2
  exit 1
fi

printf '%s\n' "$notes"
