#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_parent="${repo_dir}/.build/tests"
mkdir -p "${scratch_parent}"
scratch_dir="$(mktemp -d "${scratch_parent}/template.XXXXXX")"
trap 'rm -rf "${scratch_dir}"' EXIT

generate() {
  local output_dir="$1"
  shift
  mkdir -p "${output_dir}"
  ln -s "${repo_dir}/android" "${output_dir}/android"
  make --no-print-directory -f "${repo_dir}/Include.mk" generate \
    ROOT_DIR="${output_dir}" \
    GO_SRC="${repo_dir}/tests" \
    ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}" \
    APP_ID="games.example.fixture" \
    NO_COLOR=1 \
    "$@"
}

legacy_dir="${scratch_dir}/legacy"
podium_dir="${scratch_dir}/podium"
generate "${legacy_dir}"
generate "${podium_dir}" ALLOW_BACKUP=false SCREEN_ORIENTATION=landscape

legacy_manifest="${legacy_dir}/.build/android/app/src/main/AndroidManifest.xml"
podium_manifest="${podium_dir}/.build/android/app/src/main/AndroidManifest.xml"
generated_java="${podium_dir}/.build/android/app/src/main/java/games/example/fixture"

grep -Fq 'android:allowBackup="true"' "${legacy_manifest}"
grep -Fq 'android:allowBackup="false"' "${podium_manifest}"
grep -Fq 'android:screenOrientation="landscape"' "${podium_manifest}"
grep -Fq 'android:process=":restart"' "${podium_manifest}"
test -f "${generated_java}/MainActivity.java"
test -f "${generated_java}/OptionalMobileHooks.java"
test -f "${generated_java}/AndroidPlatformServices.java"
test -f "${generated_java}/ProcessRestartActivity.java"

if grep -R -n '@@[A-Z_][A-Z_]*@@' "${podium_dir}/.build/android"; then
  echo "unresolved Android template placeholder" >&2
  exit 1
fi

echo "run-template-tests: defaults and opt-in overrides passed"
