#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.legacy"
apk="${repo_dir}/.build/tests/android-fixtures/legacy/.build/android/app/build/outputs/apk/debug/app-debug.apk"
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
device_test_install_apk "${apk}"
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" logcat -c
device_test_launch_activity "${package}/.MainActivity"
device_test_wait_for_top_activity "${package}/.MainActivity"

pid=""
ready=false
echo "verify-legacy-device: waiting for resumed legacy process"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${pid}" ]]; then
    process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 1000 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-legacy-fixture: android-id=' <<<"${process_log}" \
        && grep -Fq 'builder-legacy-fixture: timezone=' <<<"${process_log}" \
        && grep -Fq 'onCreate: legacy setTimezone applied' <<<"${process_log}" \
        && grep -Fq 'onResume: resumeGame ok' <<<"${process_log}"; then
      ready=true
      break
    fi
  fi
  sleep 0.25
done
test "${ready}" = true

if grep -Fq 'AndroidBridge registered' <<<"${process_log}"; then
  echo "legacy fixture unexpectedly registered AndroidBridge" >&2
  exit 1
fi
if grep -Fq 'fatal error' <<<"${process_log}"; then
  echo "legacy fixture logged a fatal startup error" >&2
  exit 1
fi

sleep 2
if "${adb_cmd[@]}" shell dumpsys input_method \
    | grep -Eq 'mInputShown=true|isInputViewShown=true'; then
  echo "legacy fixture unexpectedly opened the IME" >&2
  exit 1
fi
device_test_wait_for_top_activity "${package}/.MainActivity"

echo "verify-legacy-device: PID ${pid}, legacy ID/time zone and unchanged IME passed"
