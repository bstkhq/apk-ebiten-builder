#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.hooks"
apk="${repo_dir}/.build/tests/android-fixtures/hooks/.build/android/app/build/outputs/apk/debug/app-debug.apk"
adb_bin="${ADB:-adb}"
serial="${ADB_SERIAL:-${1:-}}"
timeout_seconds="${DEVICE_TIMEOUT_SECONDS:-60}"

if [[ ! "${timeout_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "DEVICE_TIMEOUT_SECONDS must be a positive integer" >&2
  exit 2
fi

adb_cmd=("${adb_bin}")
if [[ -n "${serial}" ]]; then
  adb_cmd+=( -s "${serial}" )
fi

test -s "${apk}"

cleanup() {
  "${adb_cmd[@]}" shell am force-stop "${package}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${adb_cmd[@]}" install -r "${apk}" >/dev/null
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" shell am start -n "${package}/.MainActivity" >/dev/null

initial_pid=""
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  initial_pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${initial_pid}" ]]; then
    break
  fi
  sleep 0.25
done
test -n "${initial_pid}"

successor_pid=""
while (( SECONDS < deadline )); do
  candidate="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${candidate}" && "${candidate}" != "${initial_pid}" ]]; then
    candidate_log="$("${adb_cmd[@]}" shell logcat -d --pid="${candidate}" -t 500 GoLog:V '*:S' || true)"
    if grep -Fq 'builder-hooks-fixture: successor-ready' <<<"${candidate_log}" \
        && grep -Fq 'onResume: resumeGame ok' <<<"${candidate_log}" \
        && grep -Fq 'focus change: true' <<<"${candidate_log}"; then
      successor_pid="${candidate}"
      break
    fi
  fi
  sleep 0.25
done

test -n "${successor_pid}"
processes="$("${adb_cmd[@]}" shell ps -A | grep "${package}" | tr -d '\r')"
test "$(printf '%s\n' "${processes}" | wc -l)" -eq 1
printf '%s\n' "${processes}" | grep -Eq "[[:space:]]${package}$"

"${adb_cmd[@]}" shell logcat -d --pid="${successor_pid}" -t 500 GoLog:V '*:S' \
  | grep -Eq 'builder-hooks-fixture: no_backup=.* locale=.+$'

"${adb_cmd[@]}" shell run-as "${package}" ls -l no_backup \
  | grep -Eq '^-rw-------.*builder-hooks-restarted-v1$'
"${adb_cmd[@]}" shell dumpsys activity activities \
  | grep -m 1 'topResumedActivity' \
  | grep -Fq "${package}/.MainActivity"

"${adb_cmd[@]}" shell input keyevent KEYCODE_BACK
back_consumed=false
while (( SECONDS < deadline )); do
  test "$("${adb_cmd[@]}" shell pidof "${package}" | tr -d '\r')" = "${successor_pid}"
  if "${adb_cmd[@]}" shell logcat -d --pid="${successor_pid}" -t 500 GoLog:V '*:S' \
      | grep -Fq 'builder-hooks-fixture: back-consumed'; then
    back_consumed=true
    break
  fi
  sleep 0.25
done
test "${back_consumed}" = true
"${adb_cmd[@]}" shell dumpsys activity activities \
  | grep -m 1 'topResumedActivity' \
  | grep -Fq "${package}/.MainActivity"

echo "verify-hooks-device: PID ${initial_pid} -> ${successor_pid}, no-backup, locale and Back passed"
