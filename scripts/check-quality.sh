#!/bin/sh
set -eu

cd "$(git rev-parse --show-toplevel)"

echo "check-quality: checking working tree whitespace"
git diff --check

echo "check-quality: running format checks, static analysis, unit tests, Android lint, and debug assembly"
./gradlew spotlessCheck :app:detekt :app:test :app:lintDebug :app:assembleDebug
