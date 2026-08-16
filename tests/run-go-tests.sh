#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

(
  cd "${repo_dir}/tests/fixtures"
  go test ./...
  go vet ./...
)

echo "run-go-tests: every Go fixture compiled and passed vet"
