#!/bin/sh
set -eu

cd "$(git rev-parse --show-toplevel)"

mode="${1:-working-tree}"

case "$mode" in
    --staged)
        echo "check-fast: checking staged diff whitespace"
        git diff --check --cached
        ;;
    working-tree)
        echo "check-fast: checking working tree whitespace"
        git diff --check
        ;;
    *)
        echo "usage: $0 [--staged]" >&2
        exit 2
        ;;
esac
