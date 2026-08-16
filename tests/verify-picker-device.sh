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

if [[ ! "${timeout_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "DEVICE_TIMEOUT_SECONDS must be a positive integer" >&2
  exit 2
fi

adb_cmd=("${adb_bin}")
if [[ -n "${serial}" ]]; then
  adb_cmd+=( -s "${serial}" )
fi

test -s "${apk}"
[[ "${document_name}" =~ ^bstk-apk-builder-picker-probe-[0-9]+\.txt$ ]]
[[ "${window_dump}" =~ ^/sdcard/bstk-apk-builder-picker-window-[0-9]+\.xml$ ]]

original_go_log_tag="$("${adb_cmd[@]}" shell getprop log.tag.GoLog | tr -d '\r')"
original_stay_awake="$("${adb_cmd[@]}" shell settings get global stay_on_while_plugged_in | tr -d '\r')"

restore_go_log_tag() {
  if [[ -n "${original_go_log_tag}" ]]; then
    "${adb_cmd[@]}" shell setprop log.tag.GoLog "${original_go_log_tag}" >/dev/null
  else
    "${adb_cmd[@]}" shell 'setprop log.tag.GoLog ""' >/dev/null
  fi
}

cleanup() {
  "${adb_cmd[@]}" shell am force-stop "${package}" >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell rm -f "${document_path}" >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell am broadcast \
    -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://${document_path}" >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell rm -f "${window_dump}" >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell settings put global stay_on_while_plugged_in \
    "${original_stay_awake}" >/dev/null 2>&1 || true
  restore_go_log_tag >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${adb_cmd[@]}" shell setprop log.tag.GoLog V >/dev/null
"${adb_cmd[@]}" shell settings put global stay_on_while_plugged_in 7 >/dev/null
"${adb_cmd[@]}" shell input keyevent WAKEUP >/dev/null
"${adb_cmd[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
"${adb_cmd[@]}" shell input keyevent HOME >/dev/null
sleep 1

"${adb_cmd[@]}" install -r "${apk}" >/dev/null
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" shell mkdir -p /sdcard/Download
"${adb_cmd[@]}" shell "printf '%s\\n' '${document_payload}' > '${document_path}'"
"${adb_cmd[@]}" shell am broadcast \
  -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://${document_path}" >/dev/null
"${adb_cmd[@]}" shell am start -W -n "${package}/.MainActivity" >/dev/null

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
"${adb_cmd[@]}" shell dumpsys activity activities \
  | grep -m 1 'topResumedActivity' \
  | grep -Fq "${package}/.MainActivity"
if "${adb_cmd[@]}" shell run-as "${package}" ls cache/picked-files 2>/dev/null \
    | grep -q .; then
  echo "picker fixture left an application-owned cache file behind" >&2
  exit 1
fi

echo "verify-picker-device: PID ${pid}; copy, ownership and cancellation passed"
