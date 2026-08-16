#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.back"
apk="${repo_dir}/.build/tests/android-fixtures/back/.build/android/app/build/outputs/apk/debug/app-debug.apk"
adb_bin="${ADB:-adb}"
serial="${ADB_SERIAL:-${1:-}}"
timeout_seconds="${DEVICE_TIMEOUT_SECONDS:-180}"

adb_cmd=("${adb_bin}")
if [[ -n "${serial}" ]]; then
  adb_cmd+=( -s "${serial}" )
fi

# shellcheck source=device-test-lib.sh
source "${repo_dir}/tests/device-test-lib.sh"

test -s "${apk}"

cleanup() {
  device_test_session_cleanup "${package}"
}
trap cleanup EXIT

device_test_session_begin
"${adb_cmd[@]}" install -r "${apk}" >/dev/null
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" logcat -c
device_test_launch_activity "${package}/.MainActivity"
device_test_wait_for_top_activity "${package}/.MainActivity"

pid=""
ready=false
echo "verify-back-device: waiting for BackBridge handler"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${pid}" ]]; then
    process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 1500 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-back-fixture: handler-ready' <<<"${process_log}" \
        && grep -Fq 'onCreate: BackBridge registered' <<<"${process_log}" \
        && grep -Fq 'onResume: resumeGame ok' <<<"${process_log}"; then
      ready=true
      break
    fi
  fi
  sleep 0.25
done
test "${ready}" = true

device_test_wait_for_top_activity "${package}/.MainActivity"
"${adb_cmd[@]}" shell input keyevent KEYCODE_BACK
first_consumed=false
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2000 GoLog:V '*:S' || true)"
  if grep -Fq 'builder-back-fixture: call=1 consumed=true' <<<"${process_log}"; then
    first_consumed=true
    break
  fi
  sleep 0.25
done
test "${first_consumed}" = true
test "$("${adb_cmd[@]}" shell pidof "${package}" | tr -d '\r')" = "${pid}"
device_test_wait_for_top_activity "${package}/.MainActivity"

"${adb_cmd[@]}" shell input keyevent KEYCODE_BACK
second_fell_through=false
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2500 GoLog:V '*:S' || true)"
  current_top="$(device_test_top_activity)"
  if grep -Fq 'builder-back-fixture: call=2 consumed=false' <<<"${process_log}" \
      && ! grep -Fq "${package}/.MainActivity" <<<"${current_top}"; then
    second_fell_through=true
    break
  fi
  sleep 0.25
done
test "${second_fell_through}" = true
device_test_wait_until_not_top_activity "${package}/.MainActivity"
test "$(grep -Fc 'builder-back-fixture: call=1 consumed=true' <<<"${process_log}")" -eq 1
test "$(grep -Fc 'builder-back-fixture: call=2 consumed=false' <<<"${process_log}")" -eq 1
if grep -Fq 'builder-back-fixture: call=3' <<<"${process_log}"; then
  echo "one physical Back event was delivered to Go more than once" >&2
  exit 1
fi

echo "verify-back-device: consumed Back kept PID ${pid}; false Back preserved Activity fallback"
