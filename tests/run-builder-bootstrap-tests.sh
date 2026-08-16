#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_parent="${repo_dir}/.build/tests"
mkdir -p "${scratch_parent}"
scratch_dir="$(mktemp -d "${scratch_parent}/bootstrap.XXXXXX")"
trap 'rm -rf "${scratch_dir}"' EXIT

upstream_dir="${scratch_dir}/upstream"
remote_dir="${scratch_dir}/remote.git"
consumer_dir="${scratch_dir}/consumer"
commit_consumer_dir="${scratch_dir}/commit-consumer"
fixture_makefile="${repo_dir}/tests/fixtures/bootstrap/Makefile"

git init --quiet "${upstream_dir}"
git -C "${upstream_dir}" config user.email tests@example.invalid
git -C "${upstream_dir}" config user.name "Builder tests"
git -C "${upstream_dir}" branch -M trunk

write_include() {
  local marker="$1"
  printf '%s\n' \
    "BUILDER_MARKER := ${marker}" \
    '.PHONY: builder-marker' \
    'builder-marker:' \
    $'\t@printf "%s\\n" "$(BUILDER_MARKER)"' \
    > "${upstream_dir}/Include.mk"
}

write_include one
git -C "${upstream_dir}" add Include.mk
git -C "${upstream_dir}" commit --quiet -m first
first_commit="$(git -C "${upstream_dir}" rev-parse HEAD)"
git -C "${upstream_dir}" tag v1.0.0

git init --bare --quiet "${remote_dir}"
git -C "${upstream_dir}" remote add origin "${remote_dir}"
git -C "${upstream_dir}" push --quiet --tags origin trunk
git -C "${remote_dir}" symbolic-ref HEAD refs/heads/trunk

mkdir -p "${consumer_dir}"
cp "${fixture_makefile}" "${consumer_dir}/Makefile"

run_consumer() {
  local target_dir="$1"
  shift
  (
    cd "${target_dir}"
    make --no-print-directory builder-marker BUILDER_REPO="${remote_dir}" "$@"
  )
}

test "$(run_consumer "${consumer_dir}" BUILDER_REF=)" = one
test "$(git -C "${consumer_dir}/.build/apk-ebiten-builder" rev-parse HEAD)" = "${first_commit}"

write_include two
git -C "${upstream_dir}" add Include.mk
git -C "${upstream_dir}" commit --quiet -m second
second_commit="$(git -C "${upstream_dir}" rev-parse HEAD)"
git -C "${upstream_dir}" tag v2.0.0
git -C "${upstream_dir}" push --quiet --tags origin trunk

test "$(run_consumer "${consumer_dir}" BUILDER_REF=)" = two
test "$(git -C "${consumer_dir}/.build/apk-ebiten-builder" rev-parse HEAD)" = "${second_commit}"
test "$(run_consumer "${consumer_dir}" BUILDER_REF=v1.0.0)" = one
test "$(git -C "${consumer_dir}/.build/apk-ebiten-builder" rev-parse HEAD)" = "${first_commit}"

# Once a ref is present locally, a pinned build does not need the remote.
offline_remote_dir="${scratch_dir}/remote.offline"
mv "${remote_dir}" "${offline_remote_dir}"
test "$(run_consumer "${consumer_dir}" BUILDER_REF=v1.0.0)" = one
mv "${offline_remote_dir}" "${remote_dir}"

mkdir -p "${commit_consumer_dir}"
cp "${fixture_makefile}" "${commit_consumer_dir}/Makefile"
test "$(run_consumer "${commit_consumer_dir}" BUILDER_REF="${second_commit}")" = two
test "$(git -C "${commit_consumer_dir}/.build/apk-ebiten-builder" rev-parse HEAD)" = "${second_commit}"

if run_consumer "${consumer_dir}" BUILDER_REF=does-not-exist >/dev/null 2>&1; then
  echo "an unknown BUILDER_REF was accepted" >&2
  exit 1
fi

echo "run-builder-bootstrap-tests: latest default branch, release tag and commit pinning passed"
