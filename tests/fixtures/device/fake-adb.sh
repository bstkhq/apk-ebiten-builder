#!/usr/bin/env bash
set -euo pipefail

state_dir="${FAKE_ADB_STATE:?FAKE_ADB_STATE is required}"
mkdir -p "${state_dir}"

if [[ "${1:-}" == -s ]]; then
  test -n "${2:-}"
  shift 2
fi

printf '%s\n' "$*" >> "${state_dir}/commands"

if [[ "${1:-}" == get-state ]]; then
  printf '%s\n' device
  exit 0
fi

if [[ "${1:-}" == install ]]; then
  test "${2:-}" = -r
  test "${3:-}" = -d
  test -n "${4:-}"
  test "$#" -eq 4
  exit 0
fi

if [[ "${1:-}" != shell ]]; then
  echo "unsupported fake adb command: $*" >&2
  exit 2
fi
shift
command="$*"

if [[ -n "${FAKE_ADB_FAIL_COMMAND:-}" \
    && "${command}" == "${FAKE_ADB_FAIL_COMMAND}" ]]; then
  exit 1
fi

case "${command}" in
  'getprop log.tag.GoLog')
    cat "${state_dir}/go-log"
    ;;
  setprop\ log.tag.GoLog\ [VDIWEFS])
    printf '%s\n' "${command##* }" > "${state_dir}/go-log"
    ;;
  'setprop log.tag.GoLog ""')
    : > "${state_dir}/go-log"
    ;;
  'settings get global stay_on_while_plugged_in')
    cat "${state_dir}/stay-awake"
    ;;
  settings\ put\ global\ stay_on_while_plugged_in\ [0-9]*)
    value="${command##* }"
    [[ "${value}" =~ ^[0-9]+$ ]]
    printf '%s\n' "${value}" > "${state_dir}/stay-awake"
    ;;
  'settings delete global stay_on_while_plugged_in')
    printf '%s\n' null > "${state_dir}/stay-awake"
    ;;
  'dumpsys activity activities')
    cat "${state_dir}/top-activity"
    count="$(cat "${state_dir}/top-count")"
    printf '%s\n' "$((count + 1))" > "${state_dir}/top-count"
    ;;
  'dumpsys window')
    printf '%s\n' 'mCurrentFocus=Window{fixture}'
    ;;
  'input keyevent KEYCODE_WAKEUP'|\
  'wm dismiss-keyguard'|\
  'cmd statusbar collapse'|\
  'input keyevent KEYCODE_HOME')
    ;;
  am\ force-stop\ *)
    ;;
  am\ start\ -W\ -n\ *)
    printf '%s\n' 'Status: ok'
    ;;
  *)
    echo "unsupported fake adb shell command: ${command}" >&2
    exit 2
    ;;
esac
