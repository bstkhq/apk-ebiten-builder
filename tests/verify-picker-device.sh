#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.picker"
apk="${repo_dir}/.build/tests/android-fixtures/picker/.build/android/app/build/outputs/apk/debug/app-debug.apk"
adb_bin="${ADB:-adb}"
serial="${ADB_SERIAL:-${1:-}}"
timeout_seconds="${DEVICE_TIMEOUT_SECONDS:-180}"
document_name="bstk-apk-builder-picker-probe-$$.txt"
document_path="/sdcard/Download/${document_name}"
window_dump="/sdcard/bstk-apk-builder-picker-window-$$.xml"
document_payload="builder-picker-fixture-payload-v1"

adb_cmd=("${adb_bin}")
if [[ -n "${serial}" ]]; then
  adb_cmd+=( -s "${serial}" )
fi

# shellcheck source=device-test-lib.sh
source "${repo_dir}/tests/device-test-lib.sh"

test -s "${apk}"
[[ "${document_name}" =~ ^bstk-apk-builder-picker-probe-[0-9]+\.txt$ ]]
[[ "${window_dump}" =~ ^/sdcard/bstk-apk-builder-picker-window-[0-9]+\.xml$ ]]

cleanup() {
  "${adb_cmd[@]}" shell rm -f "${document_path}" >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell am broadcast \
    -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://${document_path}" >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell rm -f "${window_dump}" >/dev/null 2>&1 || true
  device_test_session_cleanup "${package}"
}
trap cleanup EXIT

device_test_session_begin
device_test_install_apk "${apk}"
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" logcat -c
"${adb_cmd[@]}" shell mkdir -p /sdcard/Download
"${adb_cmd[@]}" shell "printf '%s\\n' '${document_payload}' > '${document_path}'"
"${adb_cmd[@]}" shell am broadcast \
  -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://${document_path}" >/dev/null
device_test_launch_activity "${package}/.MainActivity"

pid=""
document_bounds=""
echo "verify-picker-device: waiting for Android's document picker"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${pid}" ]]; then
    process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 1000 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-picker-fixture: result-error=' <<<"${process_log}"; then
      printf '%s\n' "${process_log}" >&2
      exit 1
    fi
  fi

  "${adb_cmd[@]}" shell uiautomator dump "${window_dump}" \
    >/dev/null 2>&1 || true
  picker_window="$("${adb_cmd[@]}" exec-out cat "${window_dump}" \
    2>/dev/null | tr -d '\r' || true)"
  document_bounds="$(DOCUMENT_NAME="${document_name}" perl -ne '
    my $name = quotemeta($ENV{DOCUMENT_NAME});
    if (/text="$name"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/) {
      print int(($1 + $3) / 2), " ", int(($2 + $4) / 2);
      exit;
    }
  ' <<<"${picker_window}")"
  if [[ -n "${document_bounds}" ]]; then
    break
  fi
  sleep 0.25
done
test -n "${pid}"
test -n "${document_bounds}"

read -r document_x document_y <<<"${document_bounds}"
"${adb_cmd[@]}" shell input tap "${document_x}" "${document_y}"

selection_ready=false
echo "verify-picker-device: waiting for copied selection and cancellation request"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2000 GoLog:V '*:S' || true)"
  if grep -Fq 'builder-picker-fixture: result-error=' <<<"${process_log}"; then
    printf '%s\n' "${process_log}" >&2
    exit 1
  fi
  if grep -Fq 'builder-picker-fixture: selection-ok bytes=34' <<<"${process_log}" \
      && grep -Fq 'builder-picker-fixture: cancellation-requested' <<<"${process_log}"; then
    selection_ready=true
    break
  fi
  sleep 0.25
done
test "${selection_ready}" = true

"${adb_cmd[@]}" shell input keyevent BACK >/dev/null

cancellation_ready=false
echo "verify-picker-device: waiting for cancellation callback"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2500 GoLog:V '*:S' || true)"
  if grep -Fq 'builder-picker-fixture: result-error=' <<<"${process_log}"; then
    printf '%s\n' "${process_log}" >&2
    exit 1
  fi
  if grep -Fq 'builder-picker-fixture: cancellation-ok' <<<"${process_log}" \
      && grep -Fq 'onResume: resumeGame ok' <<<"${process_log}"; then
    cancellation_ready=true
    break
  fi
  sleep 0.25
done
test "${cancellation_ready}" = true

test "$(grep -Fc 'builder-picker-fixture: selection-ok' <<<"${process_log}")" -eq 1
test "$(grep -Fc 'builder-picker-fixture: cancellation-ok' <<<"${process_log}")" -eq 1
test "$("${adb_cmd[@]}" shell pidof "${package}" | tr -d '\r')" = "${pid}"
device_test_wait_for_top_activity "${package}/.MainActivity"
if "${adb_cmd[@]}" shell run-as "${package}" ls cache/picked-files 2>/dev/null \
    | grep -q .; then
  echo "picker fixture left an application-owned cache file behind" >&2
  exit 1
fi

echo "verify-picker-device: PID ${pid}; copy, ownership and cancellation passed"
