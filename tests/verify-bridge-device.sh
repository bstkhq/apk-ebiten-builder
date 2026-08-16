#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.bridge"
apk="${repo_dir}/.build/tests/android-fixtures/bridge/.build/android/app/build/outputs/apk/debug/app-debug.apk"
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

initial_pid=""
initial_ready=false
echo "verify-bridge-device: waiting for every AndroidBridge service"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  initial_pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${initial_pid}" ]]; then
    initial_log="$("${adb_cmd[@]}" logcat -d --pid="${initial_pid}" -t 2000 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-bridge-fixture: runtime-ok' <<<"${initial_log}"; then
      initial_ready=true
      break
    fi
    if grep -Fq 'builder-bridge-fixture: runtime-error=' <<<"${initial_log}"; then
      printf '%s\n' "${initial_log}" >&2
      exit 1
    fi
  fi
  sleep 0.25
done
test "${initial_ready}" = true

grep -Eq 'builder-bridge-fixture: android-id=[[:xdigit:]]{1,16}$' <<<"${initial_log}"
grep -Eq 'builder-bridge-fixture: device=.+/.+ android=.+ sdk=[1-9][0-9]* package=games.example.builder.bridge$' \
  <<<"${initial_log}"
grep -Fq 'builder-bridge-fixture: version=v1.0.2/1000200' <<<"${initial_log}"
grep -Eq 'builder-bridge-fixture: timezone=.+ locales=.+$' <<<"${initial_log}"
grep -Eq 'builder-bridge-fixture: dirs=/data/.+\|/data/.+\|/data/.+$' <<<"${initial_log}"
grep -Eq 'builder-bridge-fixture: power=([01](\.[0-9]+)?) plugged=(true|false) interactive=(true|false) save=(true|false)$' \
  <<<"${initial_log}"
grep -Eq 'builder-bridge-fixture: network=[^ ]* metered=(true|false) ips=[^ ]*$' \
  <<<"${initial_log}"
grep -Fq 'onCreate: AndroidBridge registered' <<<"${initial_log}"

successor_pid=""
successor_ready=false
echo "verify-bridge-device: waiting for Binder-confirmed process replacement"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  candidate="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${candidate}" && "${candidate}" != "${initial_pid}" ]]; then
    successor_log="$("${adb_cmd[@]}" logcat -d --pid="${candidate}" -t 2000 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-bridge-fixture: successor-ready' <<<"${successor_log}" \
        && grep -Fq 'onResume: resumeGame ok' <<<"${successor_log}"; then
      successor_pid="${candidate}"
      successor_ready=true
      break
    fi
  fi
  sleep 0.25
done
test "${successor_ready}" = true
device_test_wait_for_top_activity "${package}/.MainActivity"

all_logs="$("${adb_cmd[@]}" logcat -d -t 4000 GoLog:V '*:S')"
grep -Fq "restart: death observer linked; terminating process ${initial_pid}" <<<"${all_logs}"
grep -Fq "restart: death confirmed for process ${initial_pid}; launching successor" <<<"${all_logs}"
if grep -Eq 'builder-bridge-fixture: (runtime|restart)-error=' <<<"${all_logs}"; then
  printf '%s\n' "${all_logs}" >&2
  exit 1
fi

processes="$("${adb_cmd[@]}" shell ps -A | grep "${package}" | tr -d '\r')"
test "$(printf '%s\n' "${processes}" | wc -l)" -eq 1
printf '%s\n' "${processes}" | grep -Eq "[[:space:]]${package}$"

"${adb_cmd[@]}" shell run-as "${package}" ls -l no_backup \
  | grep -Eq '^-rw-------.*builder-bridge-restarted-v1$'
marker_identity="$(
  "${adb_cmd[@]}" shell run-as "${package}" cat \
    no_backup/builder-bridge-restarted-v1 | tr -d '\r\n'
)"
grep -Eq "^${initial_pid}:[0-9]+$" <<<"${marker_identity}"
device_test_wait_for_top_activity "${package}/.MainActivity"

# The helper is deliberately private: even adb shell cannot invoke it.
private_start="$(
  "${adb_cmd[@]}" shell am start \
    -n "${package}/.ProcessRestartActivity" \
    --ei previous_pid 1 2>&1 || true
)"
grep -Eq 'Permission Denial|SecurityException|not exported' <<<"${private_start}"
test "$("${adb_cmd[@]}" shell pidof "${package}" | tr -d '\r')" = "${successor_pid}"

echo "verify-bridge-device: PID ${initial_pid} -> ${successor_pid}; all services and safe restart passed"
