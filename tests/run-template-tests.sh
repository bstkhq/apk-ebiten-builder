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

default_dir="${scratch_dir}/default"
cleartext_true_dir="${scratch_dir}/cleartext-true"
cleartext_false_dir="${scratch_dir}/cleartext-false"
generate "${default_dir}" VERSION=v1.19.2
generate "${cleartext_true_dir}" USES_CLEARTEXT_TRAFFIC=true \
  ENABLE_ON_BACK_INVOKED_CALLBACK=true SCREEN_ORIENTATION=landscape
generate "${cleartext_false_dir}" USES_CLEARTEXT_TRAFFIC=false \
  ENABLE_ON_BACK_INVOKED_CALLBACK=false ALLOW_BACKUP=' false '

default_manifest="${default_dir}/.build/android/app/src/main/AndroidManifest.xml"
true_manifest="${cleartext_true_dir}/.build/android/app/src/main/AndroidManifest.xml"
false_manifest="${cleartext_false_dir}/.build/android/app/src/main/AndroidManifest.xml"
default_gradle="${default_dir}/.build/android/app/build.gradle"
generated_java="${default_dir}/.build/android/app/src/main/java/games/example/fixture"

grep -Fq 'android:allowBackup="true"' "${default_manifest}"
grep -Fq 'android:allowBackup="false"' "${false_manifest}"
if grep -Fq 'android:usesCleartextTraffic=' "${default_manifest}"; then
  echo "default manifest unexpectedly overrides Android cleartext policy" >&2
  exit 1
fi
if grep -Fq 'android:enableOnBackInvokedCallback=' "${default_manifest}"; then
  echo "default manifest unexpectedly overrides Android Back dispatch policy" >&2
  exit 1
fi
grep -Fq 'android:usesCleartextTraffic="true"' "${true_manifest}"
grep -Fq 'android:usesCleartextTraffic="false"' "${false_manifest}"
grep -Fq 'android:enableOnBackInvokedCallback="true"' "${true_manifest}"
grep -Fq 'android:enableOnBackInvokedCallback="false"' "${false_manifest}"
grep -Fq 'android:screenOrientation="landscape"' "${true_manifest}"
grep -Fq 'android:exported="false"' "${default_manifest}"
grep -Fq 'android:process=":restart"' "${default_manifest}"
grep -Fq 'android:theme="@style/RestartTheme"' "${default_manifest}"
grep -Fq 'versionCode 1190200' "${default_gradle}"

test -f "${generated_java}/MainActivity.java"
test -f "${generated_java}/OptionalAndroidBridge.java"
test -f "${generated_java}/OptionalBackBridge.java"
test -f "${generated_java}/OptionalFilePickerBridge.java"
test -f "${generated_java}/AndroidFilePicker.java"
test -f "${generated_java}/AndroidBridgeServices.java"
test -f "${generated_java}/ProcessRestartActivity.java"
grep -Fq 'registerAndroidBridge' "${generated_java}/OptionalAndroidBridge.java"
grep -Fq 'registerBackBridge' "${generated_java}/OptionalBackBridge.java"
grep -Fq 'registerFilePickerBridge' "${generated_java}/OptionalFilePickerBridge.java"
grep -Fq 'registerOptionalBackBridge()' "${generated_java}/MainActivity.java"
grep -Fq 'registerOptionalFilePicker()' "${generated_java}/MainActivity.java"
grep -Fq 'linkToDeath(deathRecipient, 0)' "${generated_java}/ProcessRestartActivity.java"
grep -Fq 'Process.killProcess(previousPid)' "${generated_java}/ProcessRestartActivity.java"
if grep -Fq 'ActivityManager' "${generated_java}/ProcessRestartActivity.java"; then
  echo "restart helper must not infer process death through ActivityManager" >&2
  exit 1
fi
link_line="$(grep -n 'deathToken.linkToDeath(deathRecipient, 0)' "${generated_java}/ProcessRestartActivity.java" | cut -d: -f1)"
kill_line="$(grep -n 'Process.killProcess(previousPid)' "${generated_java}/ProcessRestartActivity.java" | cut -d: -f1)"
test "${link_line}" -lt "${kill_line}"

# Existing IME behavior is deliberately outside this change.
grep -Fq 'Mobile.registerIMEBridge(new IMEBridge()' "${generated_java}/MainActivity.java"
grep -Fq 'view.prepareShowIME(inputType, imeOptions, keyboardCompatibility())' \
  "${generated_java}/MainActivity.java"
grep -Fq 'imm.restartInput(view)' "${generated_java}/MainActivity.java"
grep -Fq 'imm.showSoftInput(view, 0)' "${generated_java}/MainActivity.java"

if grep -R -n '@@[A-Z_][A-Z_]*@@' "${default_dir}/.build/android"; then
  echo "unresolved Android template placeholder" >&2
  exit 1
fi

for invalid in TRUE yes 1 'true false'; do
  invalid_dir="${scratch_dir}/invalid-${invalid// /-}"
  if generate "${invalid_dir}" USES_CLEARTEXT_TRAFFIC="${invalid}" >/dev/null 2>&1; then
    echo "invalid USES_CLEARTEXT_TRAFFIC=${invalid} was accepted" >&2
    exit 1
  fi
done

for invalid in TRUE yes 1 'true false'; do
  invalid_dir="${scratch_dir}/invalid-back-${invalid// /-}"
  if generate "${invalid_dir}" ENABLE_ON_BACK_INVOKED_CALLBACK="${invalid}" >/dev/null 2>&1; then
    echo "invalid ENABLE_ON_BACK_INVOKED_CALLBACK=${invalid} was accepted" >&2
    exit 1
  fi
done

for invalid in '' TRUE yes 1 'true false'; do
  invalid_dir="${scratch_dir}/invalid-backup-${invalid// /-}"
  if generate "${invalid_dir}" ALLOW_BACKUP="${invalid}" >/dev/null 2>&1; then
    echo "invalid ALLOW_BACKUP=${invalid} was accepted" >&2
    exit 1
  fi
done

echo "run-template-tests: defaults, strict backup/cleartext/Back options, bridges, restart and IME isolation passed"
