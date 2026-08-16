#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.legacy"
apk="${repo_dir}/.build/tests/android-fixtures/legacy/.build/android/app/build/outputs/apk/debug/app-debug.apk"
adb_bin="${ADB:-adb}"
serial="${ADB_SERIAL:-${1:-}}"
timeout_seconds="${DEVICE_TIMEOUT_SECONDS:-180}"

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

if grep -Fq 'AndroidPlatform registered' <<<"${process_log}"; then
  echo "legacy fixture unexpectedly registered AndroidPlatform" >&2
  exit 1
fi
if grep -Fq 'fatal error' <<<"${process_log}"; then
  echo "legacy fixture logged a fatal startup error" >&2
  exit 1
fi

sleep 2
if "${adb_cmd[@]}" shell dumpsys input_method | grep -Fq 'mInputShown=true'; then
  echo "legacy fixture unexpectedly opened the IME" >&2
  exit 1
fi
"${adb_cmd[@]}" shell dumpsys activity activities \
  | grep -m 1 'topResumedActivity' \
  | grep -Fq "${package}/.MainActivity"

echo "verify-legacy-device: PID ${pid}, legacy ID/time zone and unchanged IME passed"
