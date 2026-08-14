#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.legacy"
apk="${repo_dir}/.build/tests/android-fixtures/legacy/.build/android/app/build/outputs/apk/debug/app-debug.apk"
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

pid=""
ready=false
echo "verify-legacy-device: waiting for resumed, focused process"
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  pid="$("${adb_cmd[@]}" shell pidof "${package}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${pid}" ]]; then
    process_log="$("${adb_cmd[@]}" shell logcat -d --pid="${pid}" -t 500 GoLog:V '*:S' || true)"
    if grep -Fq 'onResume: resumeGame ok' <<<"${process_log}" \
        && grep -Fq 'focus change: true' <<<"${process_log}"; then
      ready=true
      break
    fi
  fi
  sleep 0.25
done
test "${ready}" = true

grep -Fq 'optional platform bridge not declared, skipping' <<<"${process_log}"
grep -Fq 'optional Back hook not declared, preserving default behavior' <<<"${process_log}"
if grep -Fq 'IMEBridge.show(' <<<"${process_log}"; then
  echo "legacy fixture unexpectedly requested the IME" >&2
  exit 1
fi

# Give Android a short stabilization window to catch an unwanted automatic
# keyboard opening caused merely by focusing the Ebiten surface.
sleep 2
if "${adb_cmd[@]}" shell dumpsys input_method | grep -Fq 'mInputShown=true'; then
  echo "legacy fixture unexpectedly opened the IME" >&2
  exit 1
fi

"${adb_cmd[@]}" shell dumpsys activity activities \
  | grep -m 1 'topResumedActivity' \
  | grep -Fq "${package}/.MainActivity"

echo "verify-legacy-device: PID ${pid}, optional hooks absent and IME hidden"
