#!/bin/sh
set -eu

cd "$(git rev-parse --show-toplevel)"

echo "check-quality: checking working tree whitespace"
git diff --check

echo "check-quality: running unit tests, Android lint, and debug assembly"
./gradlew :app:test :app:lintDebug :app:assembleDebug
