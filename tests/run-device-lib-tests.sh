#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_parent="${repo_dir}/.build/tests"
mkdir -p "${scratch_parent}"
scratch_dir="$(mktemp -d "${scratch_parent}/device-lib.XXXXXX")"
trap 'rm -rf "${scratch_dir}"' EXIT

export FAKE_ADB_STATE="${scratch_dir}/state"
mkdir -p "${FAKE_ADB_STATE}"
adb_cmd=("${repo_dir}/tests/fixtures/device/fake-adb.sh" -s fixture)
timeout_seconds=2
export DEVICE_TEST_HOME_SETTLE_SECONDS=0

# shellcheck source=device-test-lib.sh
source "${repo_dir}/tests/device-test-lib.sh"

reset_state() {
  local log_level="$1"
  local stay_awake="$2"
  printf '%s\n' "${log_level}" > "${FAKE_ADB_STATE}/go-log"
  printf '%s\n' "${stay_awake}" > "${FAKE_ADB_STATE}/stay-awake"
  printf '%s\n' \
    'topResumedActivity=ActivityRecord{fixture games.example.builder.fixture/.MainActivity}' \
    > "${FAKE_ADB_STATE}/top-activity"
  printf '%s\n' 0 > "${FAKE_ADB_STATE}/top-count"
  : > "${FAKE_ADB_STATE}/commands"
}

reset_state S 0
device_test_session_begin
test "$(tr -d '\r\n' < "${FAKE_ADB_STATE}/go-log")" = V
test "$(tr -d '\r\n' < "${FAKE_ADB_STATE}/stay-awake")" = 7
device_test_launch_activity games.example.builder.fixture/.MainActivity
device_test_wait_for_top_activity games.example.builder.fixture/.MainActivity
test "$(cat "${FAKE_ADB_STATE}/top-count")" = 4
printf '%s\n' \
  'topResumedActivity=ActivityRecord{launcher com.example.launcher/.Home}' \
  > "${FAKE_ADB_STATE}/top-activity"
printf '%s\n' 0 > "${FAKE_ADB_STATE}/top-count"
device_test_wait_until_not_top_activity games.example.builder.fixture/.MainActivity
test "$(cat "${FAKE_ADB_STATE}/top-count")" = 4
device_test_session_cleanup games.example.builder.fixture
test "$(tr -d '\r\n' < "${FAKE_ADB_STATE}/go-log")" = S
test "$(tr -d '\r\n' < "${FAKE_ADB_STATE}/stay-awake")" = 0
grep -Fq 'input keyevent KEYCODE_HOME' "${FAKE_ADB_STATE}/commands"
grep -Fq 'am start -W -n games.example.builder.fixture/.MainActivity' \
  "${FAKE_ADB_STATE}/commands"
grep -Fq 'am force-stop games.example.builder.fixture' "${FAKE_ADB_STATE}/commands"

reset_state "" null
device_test_session_begin
device_test_session_cleanup games.example.builder.fixture
test ! -s "${FAKE_ADB_STATE}/go-log"
test "$(tr -d '\r\n' < "${FAKE_ADB_STATE}/stay-awake")" = null
grep -Fq 'setprop log.tag.GoLog ""' "${FAKE_ADB_STATE}/commands"
grep -Fq 'settings delete global stay_on_while_plugged_in' \
  "${FAKE_ADB_STATE}/commands"

reset_state unexpected 0
if device_test_session_begin >/dev/null 2>&1; then
  echo "unexpected Go log level was accepted" >&2
  exit 1
fi
test "${DEVICE_TEST_SESSION_ACTIVE}" = false

echo "run-device-lib-tests: session setup, stable focus and exact restoration passed"
