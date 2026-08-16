#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.ime"
apk="${repo_dir}/.build/tests/android-fixtures/ime/.build/android/app/build/outputs/apk/debug/app-debug.apk"
adb_bin="${ADB:-adb}"
serial="${ADB_SERIAL:-${1:-}}"
timeout_seconds="${DEVICE_TIMEOUT_SECONDS:-180}"
character_timeout_seconds="${IME_CHARACTER_TIMEOUT_SECONDS:-20}"

adb_cmd=("${adb_bin}")
if [[ -n "${serial}" ]]; then
  adb_cmd+=( -s "${serial}" )
fi

# shellcheck source=device-test-lib.sh
source "${repo_dir}/tests/device-test-lib.sh"

device_test_validate_positive_integer "${character_timeout_seconds}" \
  IME_CHARACTER_TIMEOUT_SECONDS
test -s "${apk}"

cleanup() {
  device_test_session_cleanup "${package}"
}
trap cleanup EXIT

device_test_session_begin
device_test_install_apk "${apk}"
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" logcat -c
device_test_launch_activity "${package}/.MainActivity"
device_test_wait_for_top_activity "${package}/.MainActivity"

ime_is_shown() {
  "${adb_cmd[@]}" shell dumpsys input_method \
    | grep -Eq 'mInputShown=true|isInputViewShown=true'
}

pid=""
ready=false
echo "verify-ime-device: waiting for focused InputConnection"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${pid}" ]]; then
    process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2000 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-ime-fixture: requesting-ime-during-registration' <<<"${process_log}" \
        && grep -Fq 'IMEBridge.show(0x1, 0x6)' <<<"${process_log}" \
        && grep -Fq 'new input connection' <<<"${process_log}" \
        && grep -Fq 'showIme: requested through window insets' <<<"${process_log}" \
        && grep -Fq 'onResume: resumeGame ok' <<<"${process_log}" \
        && ime_is_shown; then
      ready=true
      break
    fi
  fi
  sleep 0.25
done
test "${ready}" = true

expected_text=""
for character in i m e 4 2; do
  expected_text+="${character}"
  delivered=false
  echo "verify-ime-device: waiting for text ${expected_text}"
  for attempt in 1 2 3; do
    "${adb_cmd[@]}" shell input text "${character}"
    deadline=$((SECONDS + character_timeout_seconds))
    while (( SECONDS < deadline )); do
      process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2500 GoLog:V '*:S' || true)"
      latest_text="$(
        sed -n 's/.*builder-ime-fixture: ime-text=//p' <<<"${process_log}" \
          | tail -n 1 \
          | tr -d '\r'
      )"
      if [[ "${latest_text}" == "${expected_text}" ]]; then
        delivered=true
        break
      fi
      if [[ -n "${latest_text}" && "${expected_text}" != "${latest_text}"* ]]; then
        echo "unexpected IME text: got ${latest_text}, expected prefix of ${expected_text}" >&2
        exit 1
      fi
      sleep 0.25
    done
    if [[ "${delivered}" == true ]]; then
      break
    fi
    echo "verify-ime-device: retrying character ${character} (${attempt}/3)" >&2
  done
  test "${delivered}" = true
done

hidden=false
deadline=$((SECONDS + timeout_seconds))
echo "verify-ime-device: waiting for Go-requested IME hide"
while (( SECONDS < deadline )); do
  process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 3000 GoLog:V '*:S' || true)"
  if grep -Fq 'builder-ime-fixture: requesting-hide' <<<"${process_log}" \
      && grep -Fq 'IMEBridge.hide()' <<<"${process_log}" \
      && grep -Fq 'hideIme: requested' <<<"${process_log}" \
      && ! ime_is_shown; then
    hidden=true
    break
  fi
  sleep 0.25
done
test "${hidden}" = true
test "$("${adb_cmd[@]}" shell pidof "${package}" | tr -d '\r')" = "${pid}"
device_test_wait_for_top_activity "${package}/.MainActivity"
test "$(grep -Fc 'builder-ime-fixture: ime-text=ime42' <<<"${process_log}")" -eq 1
if grep -Fq 'onCreate: fatal error' <<<"${process_log}"; then
  printf '%s\n' "${process_log}" >&2
  exit 1
fi

echo "verify-ime-device: early show, text input and Go-requested hide passed in PID ${pid}"
