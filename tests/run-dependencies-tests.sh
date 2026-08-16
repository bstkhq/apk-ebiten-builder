#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_go_mod="${repo_dir}/tests/fixtures/go.mod"
fixture_version="$(awk '
  $1 == "require" && $2 == "github.com/hajimehoshi/ebiten/v2" { print $3; exit }
  $1 == "github.com/hajimehoshi/ebiten/v2" { print $2; exit }
' "${fixture_go_mod}")"

if [[ -z "${fixture_version}" ]]; then
  echo "missing Ebitengine version in ${fixture_go_mod}" >&2
  exit 1
fi

render_install() {
  make --no-print-directory -n -f "${repo_dir}/Dependencies.mk" \
    install_ebitenmobile NO_COLOR=1 "$@"
}

default_install="$(render_install)"
grep -Fq "go install \"github.com/hajimehoshi/ebiten/v2/cmd/ebitenmobile@${fixture_version}\"" \
  <<<"${default_install}"
if grep -Fq '@latest' <<<"${default_install}"; then
  echo "the default ebitenmobile installation must be pinned" >&2
  exit 1
fi

override_version=v2.9.8
version_install="$(render_install "EBITENMOBILE_VERSION=${override_version}")"
grep -Fq "go install \"github.com/hajimehoshi/ebiten/v2/cmd/ebitenmobile@${override_version}\"" \
  <<<"${version_install}"

override_module=example.invalid/ebitenmobile@v0.0.1
module_install="$(render_install "EBITENMOBILE_MOD=${override_module}")"
grep -Fq "go install \"${override_module}\"" <<<"${module_install}"

echo "run-dependencies-tests: pinned default and overrides passed"
