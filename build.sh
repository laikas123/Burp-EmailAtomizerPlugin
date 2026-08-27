#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is required. On macOS with Homebrew: brew install gradle" >&2
  exit 1
fi
gradle clean test jar
printf '\nBuilt: build/libs/email-atomizer-0.3.12.jar\n'
