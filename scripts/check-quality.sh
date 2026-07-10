#!/bin/sh
set -eu

cd "$(git rev-parse --show-toplevel)"

echo "check-quality: checking staged and unstaged whitespace"
git diff HEAD --check

echo "check-quality: running format checks, static analysis, coverage, unit tests, Android lint, and debug assembly"
./gradlew qualityCheck
