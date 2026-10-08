#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="app/src/main/java"

# BOOSTLAB must not behave like aggressive "RAM cleaner" boosters.
forbidden_apis=(
  "killBackgroundProcesses"
  "forceStopPackage"
  "clearApplicationUserData"
  "setProcessLimit"
  "restartPackage"
)

for api in "${forbidden_apis[@]}"; do
  if grep -R -n --include='*.kt' --include='*.java' "$api" "$SOURCE_DIR"; then
    echo "Forbidden aggressive booster API detected: $api" >&2
    exit 1
  fi
done

# Do not ship unverifiable performance claims in the Android UI/source.
if grep -R -n -E --include='*.kt' --include='*.java'   '(increase FPS|boost FPS|FPS boost|\+[0-9]+% FPS)' "$SOURCE_DIR"; then
  echo "Unverifiable FPS marketing claim detected" >&2
  exit 1
fi

echo "BOOSTLAB product-safety claims check passed"
