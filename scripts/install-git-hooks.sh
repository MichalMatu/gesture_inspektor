#!/bin/sh
set -eu

cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath scripts/git-hooks
echo "Git hooks enabled from scripts/git-hooks"
