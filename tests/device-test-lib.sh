#!/usr/bin/env bash

# Shared lifecycle helpers for physical Android device gates. The caller owns
# `adb_cmd`, `timeout_seconds`, and the EXIT trap so fixture-specific cleanup
# can run before or after device_test_session_cleanup.

DEVICE_TEST_SESSION_ACTIVE=false
DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL=""
DEVICE_TEST_ORIGINAL_STAY_AWAKE=""

device_test_validate_positive_integer() {
  local value="$1"
  local name="$2"
  if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer" >&2
    return 2
  fi
}

device_test_validate_context() {
  if ! declare -p adb_cmd 2>/dev/null | grep -Fq 'declare -a'; then
    echo "device test must define adb_cmd as an array" >&2
    return 2
  fi
  device_test_validate_positive_integer "${timeout_seconds:-}" \
    DEVICE_TIMEOUT_SECONDS
}

device_test_session_begin() {
  device_test_validate_context
  if [[ "${DEVICE_TEST_SESSION_ACTIVE}" == true ]]; then
    echo "Android device test session is already active" >&2
    return 2
  fi

  local state
  state="$("${adb_cmd[@]}" get-state 2>/dev/null | tr -d '\r' || true)"
  if [[ "${state}" != device ]]; then
    echo "selected ADB target is not ready: ${state:-unavailable}" >&2
    return 1
  fi

  if ! DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL="$(
    "${adb_cmd[@]}" shell getprop log.tag.GoLog 2>/dev/null
  )"; then
    echo "cannot read log.tag.GoLog from the selected ADB target" >&2
    return 1
  fi
  DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL="${DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL//$'\r'/}"
  case "${DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL}" in
    ""|V|D|I|W|E|F|S) ;;
    *)
      echo "unexpected log.tag.GoLog value: ${DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL}" >&2
      return 1
      ;;
  esac

  if ! DEVICE_TEST_ORIGINAL_STAY_AWAKE="$(
    "${adb_cmd[@]}" shell settings get global stay_on_while_plugged_in 2>/dev/null
  )"; then
    echo "cannot read stay_on_while_plugged_in from the selected ADB target" >&2
    return 1
  fi
  DEVICE_TEST_ORIGINAL_STAY_AWAKE="${DEVICE_TEST_ORIGINAL_STAY_AWAKE//$'\r'/}"
  if [[ ! "${DEVICE_TEST_ORIGINAL_STAY_AWAKE}" =~ ^([0-9]+|null)$ ]]; then
    echo "unexpected stay_on_while_plugged_in value: ${DEVICE_TEST_ORIGINAL_STAY_AWAKE}" >&2
    return 1
  fi

  # Mark the session active before the first mutation so the caller's EXIT
  # trap restores a partially prepared device as well.
  DEVICE_TEST_SESSION_ACTIVE=true
  "${adb_cmd[@]}" shell setprop log.tag.GoLog V >/dev/null
  test "$("${adb_cmd[@]}" shell getprop log.tag.GoLog | tr -d '\r')" = V
  "${adb_cmd[@]}" shell settings put global stay_on_while_plugged_in 7 >/dev/null
  test "$("${adb_cmd[@]}" shell settings get global stay_on_while_plugged_in \
    | tr -d '\r')" = 7

  "${adb_cmd[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null
  "${adb_cmd[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell cmd statusbar collapse >/dev/null 2>&1 || true
  "${adb_cmd[@]}" shell input keyevent KEYCODE_HOME >/dev/null
  sleep "${DEVICE_TEST_HOME_SETTLE_SECONDS:-1}"
}

device_test_session_cleanup() {
  local package="$1"
  if [[ "${package}" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$ ]]; then
    "${adb_cmd[@]}" shell am force-stop "${package}" >/dev/null 2>&1 || true
  else
    echo "refusing to force-stop invalid Android package: ${package}" >&2
  fi

  if [[ "${DEVICE_TEST_SESSION_ACTIVE:-false}" != true ]]; then
    return
  fi

  if [[ -n "${DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL}" ]]; then
    "${adb_cmd[@]}" shell setprop log.tag.GoLog \
      "${DEVICE_TEST_ORIGINAL_GO_LOG_LEVEL}" >/dev/null 2>&1 || true
  else
    "${adb_cmd[@]}" shell 'setprop log.tag.GoLog ""' >/dev/null 2>&1 || true
  fi

  if [[ "${DEVICE_TEST_ORIGINAL_STAY_AWAKE}" == null ]]; then
    "${adb_cmd[@]}" shell settings delete global stay_on_while_plugged_in \
      >/dev/null 2>&1 || true
  else
    "${adb_cmd[@]}" shell settings put global stay_on_while_plugged_in \
      "${DEVICE_TEST_ORIGINAL_STAY_AWAKE}" >/dev/null 2>&1 || true
  fi
  DEVICE_TEST_SESSION_ACTIVE=false
}

device_test_launch_activity() {
  local component="$1"
  "${adb_cmd[@]}" shell am start -W -n "${component}" >/dev/null
}

device_test_install_apk() {
  local apk="$1"
  test -s "${apk}"
  # Fixtures are debuggable APKs. Allow a lower versionCode so a gate remains
  # repeatable after its version fixture changes between revisions.
  "${adb_cmd[@]}" install -r -d "${apk}" >/dev/null
}

device_test_top_activity() {
  "${adb_cmd[@]}" shell dumpsys activity activities \
    | grep -m 1 'topResumedActivity' || true
}

device_test_wait_for_top_activity() {
  local component="$1"
  local stable_samples="${DEVICE_TEST_STABLE_TOP_SAMPLES:-4}"
  device_test_validate_positive_integer "${stable_samples}" \
    DEVICE_TEST_STABLE_TOP_SAMPLES

  local deadline=$((SECONDS + timeout_seconds))
  local consecutive=0
  local top
  while (( SECONDS < deadline )); do
    top="$(device_test_top_activity)"
    if grep -Fq "${component}" <<<"${top}"; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= stable_samples )); then
        return 0
      fi
    else
      consecutive=0
    fi
    sleep 0.25
  done

  echo "${component} did not remain top-resumed; focus is: $(
    "${adb_cmd[@]}" shell dumpsys window \
      | grep -m 1 'mCurrentFocus=' || true
  )" >&2
  return 1
}

device_test_wait_until_not_top_activity() {
  local component="$1"
  local stable_samples="${DEVICE_TEST_STABLE_TOP_SAMPLES:-4}"
  device_test_validate_positive_integer "${stable_samples}" \
    DEVICE_TEST_STABLE_TOP_SAMPLES

  local deadline=$((SECONDS + timeout_seconds))
  local consecutive=0
  local top
  while (( SECONDS < deadline )); do
    top="$(device_test_top_activity)"
    if [[ -n "${top}" ]] && ! grep -Fq "${component}" <<<"${top}"; then
      consecutive=$((consecutive + 1))
      if (( consecutive >= stable_samples )); then
        return 0
      fi
    else
      consecutive=0
    fi
    sleep 0.25
  done

  echo "${component} remained top-resumed" >&2
  return 1
}
