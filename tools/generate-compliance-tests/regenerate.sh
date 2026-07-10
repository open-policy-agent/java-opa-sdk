#!/usr/bin/env bash
#
# Regenerate the Rego compliance test fixtures from the OPA version pinned in
# this tool's go.mod. Run this after bumping the OPA dependency (e.g. from a
# Dependabot PR) and commit the resulting changes under TestData/.
#
# Usage: tools/generate-compliance-tests/regenerate.sh
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out_dir="$script_dir/../../opa-evaluator/src/test/resources/compliance/Tests/RegoComplianceTests/TestData/v1"

# Regenerate from scratch so fixtures removed upstream do not linger.
rm -rf "$out_dir"
mkdir -p "$out_dir"

# The generator reads ./exceptions.yaml from the working directory.
cd "$script_dir"
go run . "$out_dir"

echo "Regenerated compliance fixtures in $out_dir"
