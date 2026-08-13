#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT must point at an installed Android SDK}"

build_parent="${repo_dir}/.build/tests/android-fixtures"
mkdir -p "${build_parent}"

build_fixture() {
  local fixture="$1"
  local backup="$2"
  local back_invoked="$3"
  local root_dir="${build_parent}/${fixture}"

  mkdir -p "${root_dir}"
  if [[ ! -e "${root_dir}/android" ]]; then
    ln -s "${repo_dir}/android" "${root_dir}/android"
  fi

  make --no-print-directory -f "${repo_dir}/Include.mk" clean build \
    ROOT_DIR="${root_dir}" \
    GO_SRC="${repo_dir}/tests/fixtures/${fixture}" \
    APP_NAME="Builder ${fixture} fixture" \
    APP_ID="games.example.builder.${fixture}" \
    ALLOW_BACKUP="${backup}" \
    ENABLE_ON_BACK_INVOKED_CALLBACK="${back_invoked}" \
    SCREEN_ORIENTATION=landscape \
    ANDROID_TARGET="${ANDROID_TARGET:-android/amd64}" \
    VERSION="v1.0.1-${fixture}" \
    NO_COLOR=1

  test -s "${root_dir}/.build/android/app/libs/game.aar"
  test -s "${root_dir}/.build/android/app/build/outputs/apk/debug/app-debug.apk"

  (
    cd "${root_dir}/.build/android"
    ./gradlew -q --console=plain --warning-mode=none lintDebug
  )
}

build_fixture legacy true false
build_fixture hooks false true

echo "build-android-fixtures: legacy and hooks APKs passed"
