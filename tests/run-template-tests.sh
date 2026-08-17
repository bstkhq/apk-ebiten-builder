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
  if [[ ! -e "${output_dir}/android" ]]; then
    ln -s "${repo_dir}/android" "${output_dir}/android"
  fi
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
zero_major_dir="${scratch_dir}/zero-major"
padded_components_dir="${scratch_dir}/padded-components"
app_resources_dir="${scratch_dir}/app-resources"
resource_fixture_dir="${repo_dir}/tests/fixtures/resources"
generate "${default_dir}" VERSION=v1.19.2
generate "${cleartext_true_dir}" USES_CLEARTEXT_TRAFFIC=true \
  ENABLE_ON_BACK_INVOKED_CALLBACK=true SCREEN_ORIENTATION=landscape
generate "${cleartext_false_dir}" USES_CLEARTEXT_TRAFFIC=false \
  ENABLE_ON_BACK_INVOKED_CALLBACK=false ALLOW_BACKUP=' false '
generate "${zero_major_dir}" VERSION=v0.8.9
generate "${padded_components_dir}" VERSION=v01.08.09.07
generate "${app_resources_dir}" APP_RES_DIR="${resource_fixture_dir}"

default_manifest="${default_dir}/.build/android/app/src/main/AndroidManifest.xml"
true_manifest="${cleartext_true_dir}/.build/android/app/src/main/AndroidManifest.xml"
false_manifest="${cleartext_false_dir}/.build/android/app/src/main/AndroidManifest.xml"
default_gradle="${default_dir}/.build/android/app/build.gradle"
zero_major_gradle="${zero_major_dir}/.build/android/app/build.gradle"
padded_components_gradle="${padded_components_dir}/.build/android/app/build.gradle"
generated_java="${default_dir}/.build/android/app/src/main/java/games/example/fixture"
app_icon="${app_resources_dir}/.build/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"

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
grep -Fq 'versionCode 80900' "${zero_major_gradle}"
grep -Fq 'versionCode 1080907' "${padded_components_gradle}"
if grep -Eq 'versionCode 0[0-9]+' "${zero_major_gradle}" "${padded_components_gradle}"; then
  echo "versionCode was emitted as a leading-zero Gradle literal" >&2
  exit 1
fi

test -f "${generated_java}/MainActivity.java"
test -f "${generated_java}/OptionalAndroidBridge.java"
test -f "${generated_java}/OptionalBackBridge.java"
test -f "${generated_java}/OptionalFilePickerBridge.java"
test -f "${generated_java}/AndroidFilePicker.java"
test -f "${generated_java}/AndroidBridgeServices.java"
test -f "${generated_java}/ProcessRestartActivity.java"
grep -Fq '<!-- app-icon-overlay -->' "${app_icon}"
test -f "${app_resources_dir}/.build/android/app/src/main/res/mipmap-mdpi/ic_launcher.png"
generate "${app_resources_dir}" APP_RES_DIR="${repo_dir}/android/app/src/main/res"
if grep -Fq '<!-- app-icon-overlay -->' "${app_icon}"; then
  echo "updated APP_RES_DIR did not replace the generated icon" >&2
  exit 1
fi
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

grep -Fq 'Mobile.registerIMEBridge(new IMEBridge()' "${generated_java}/MainActivity.java"
grep -Fq 'view.prepareShowIME(inputType, imeOptions, keyboardCompatibility())' \
  "${generated_java}/MainActivity.java"
grep -Fq 'public boolean onCheckIsTextEditor()' "${generated_java}/EbitenExtendedView.java"
grep -Fq 'return this.currentInputType >= 0;' "${generated_java}/EbitenExtendedView.java"
grep -Fq 'showImeWhenReady(view, generation)' "${generated_java}/MainActivity.java"
grep -Fq '!view.hasWindowFocus() || !view.isFocused()' "${generated_java}/MainActivity.java"
grep -Fq 'imm.restartInput(view)' "${generated_java}/MainActivity.java"
grep -Fq 'insetsController.show(WindowInsetsCompat.Type.ime())' \
  "${generated_java}/MainActivity.java"
grep -Fq 'imeRequestGeneration++;' "${generated_java}/MainActivity.java"
grep -Fq 'imeShowPending = false;' "${generated_java}/MainActivity.java"

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

if generate "${scratch_dir}/invalid-app-resources" \
  APP_RES_DIR="${scratch_dir}/does-not-exist" >/dev/null 2>&1; then
  echo "missing APP_RES_DIR was accepted" >&2
  exit 1
fi

echo "run-template-tests: defaults, app resources, strict backup/cleartext/Back options, bridges, restart and focused IME lifecycle passed"
