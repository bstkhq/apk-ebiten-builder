#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package="games.example.builder.back"
apk="${repo_dir}/.build/tests/android-fixtures/back/.build/android/app/build/outputs/apk/debug/app-debug.apk"
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

original_go_log_level="$(
  "${adb_cmd[@]}" shell getprop log.tag.GoLog 2>/dev/null | tr -d '\r' || true
)"
case "${original_go_log_level}" in
  ""|V|D|I|W|E|F|S) ;;
  *)
    echo "unexpected log.tag.GoLog value: ${original_go_log_level}" >&2
    exit 1
    ;;
esac
original_stay_awake="$(
  "${adb_cmd[@]}" shell settings get global stay_on_while_plugged_in \
    2>/dev/null | tr -d '\r' || true
)"
if [[ ! "${original_stay_awake}" =~ ^[0-9]+$ ]]; then
  echo "unexpected stay_on_while_plugged_in value: ${original_stay_awake}" >&2
  exit 1
fi

cleanup() {
  "${adb_cmd[@]}" shell am force-stop "${package}" >/dev/null 2>&1 || true
  if [[ -n "${original_go_log_level}" ]]; then
    "${adb_cmd[@]}" shell setprop log.tag.GoLog "${original_go_log_level}" \
      >/dev/null 2>&1 || true
  else
    "${adb_cmd[@]}" shell 'setprop log.tag.GoLog ""' >/dev/null 2>&1 || true
  fi
  "${adb_cmd[@]}" shell settings put global stay_on_while_plugged_in \
    "${original_stay_awake}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${adb_cmd[@]}" shell setprop log.tag.GoLog V
test "$("${adb_cmd[@]}" shell getprop log.tag.GoLog | tr -d '\r')" = V
"${adb_cmd[@]}" shell settings put global stay_on_while_plugged_in 7
"${adb_cmd[@]}" shell input keyevent KEYCODE_WAKEUP
"${adb_cmd[@]}" shell wm dismiss-keyguard
"${adb_cmd[@]}" install -r "${apk}" >/dev/null
"${adb_cmd[@]}" shell pm clear "${package}" >/dev/null
"${adb_cmd[@]}" logcat -c
"${adb_cmd[@]}" shell cmd statusbar collapse >/dev/null 2>&1 || true
# HOME gives OEM SystemUI implementations that ignore `statusbar collapse` a
# deterministic surface to leave before the fixture is launched.
"${adb_cmd[@]}" shell input keyevent KEYCODE_HOME
"${adb_cmd[@]}" shell am start -n "${package}/.MainActivity" >/dev/null

top_activity() {
  "${adb_cmd[@]}" shell dumpsys activity activities \
    | grep -m 1 'topResumedActivity' || true
}

wait_for_activity_on_top() {
  local deadline=$((SECONDS + timeout_seconds))
  local top
  while (( SECONDS < deadline )); do
    top="$(top_activity)"
    if grep -Fq "${package}/.MainActivity" <<<"${top}"; then
      return 0
    fi
    sleep 0.25
  done
  echo "Back fixture did not become top-resumed; focus is: $(
    "${adb_cmd[@]}" shell dumpsys window \
      | grep -m 1 'mCurrentFocus=' || true
  )" >&2
  return 1
}

wait_for_activity_on_top

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

wait_for_activity_on_top
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
wait_for_activity_on_top

"${adb_cmd[@]}" shell input keyevent KEYCODE_BACK
second_fell_through=false
deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
  process_log="$("${adb_cmd[@]}" logcat -d --pid="${pid}" -t 2500 GoLog:V '*:S' || true)"
  top_activity="$(top_activity)"
  if grep -Fq 'builder-back-fixture: call=2 consumed=false' <<<"${process_log}" \
      && ! grep -Fq "${package}/.MainActivity" <<<"${top_activity}"; then
    second_fell_through=true
    break
  fi
  sleep 0.25
done
test "${second_fell_through}" = true
test "$(grep -Fc 'builder-back-fixture: call=1 consumed=true' <<<"${process_log}")" -eq 1
test "$(grep -Fc 'builder-back-fixture: call=2 consumed=false' <<<"${process_log}")" -eq 1
if grep -Fq 'builder-back-fixture: call=3' <<<"${process_log}"; then
  echo "one physical Back event was delivered to Go more than once" >&2
  exit 1
fi

echo "verify-back-device: consumed Back kept PID ${pid}; false Back preserved Activity fallback"
