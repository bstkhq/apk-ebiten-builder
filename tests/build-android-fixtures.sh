#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_sdk_root="${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}"
android_target="${ANDROID_TARGET:-android/amd64,android/arm64}"
build_tools="${android_sdk_root}/build-tools/35.0.0"
ndk_tools="${android_sdk_root}/ndk/26.3.11579264/toolchains/llvm/prebuilt/linux-x86_64/bin"
build_parent="${repo_dir}/.build/tests/android-fixtures"

export ANDROID_SDK_ROOT="${android_sdk_root}"
export ANDROID_HOME="${android_sdk_root}"
export PATH="$(go env GOPATH)/bin:${android_sdk_root}/platform-tools:${PATH}"
# gomobile compiles its generated gobind package outside the source checkout.
# Go 1.24+ otherwise attempts VCS stamping from that temporary directory.
export GOFLAGS="${GOFLAGS:+${GOFLAGS} }-buildvcs=false"

for required in \
  ebitenmobile \
  javap \
  unzip \
  "${android_sdk_root}/cmdline-tools/latest/bin/apkanalyzer" \
  "${build_tools}/zipalign" \
  "${build_tools}/apksigner" \
  "${ndk_tools}/llvm-readelf"; do
  if [[ "${required}" == */* ]]; then
    test -x "${required}"
  else
    command -v "${required}" >/dev/null
  fi
done

mkdir -p "${build_parent}"

build_fixture() {
  local fixture="$1"
  local version="$2"
  local cleartext="$3"
  local back_invoked="$4"
  local allow_backup="$5"
  local expected_version_code="$6"
  local app_res_dir="${7:-}"
  local root_dir="${build_parent}/${fixture}"
  local app_id="games.example.builder.${fixture}"

  mkdir -p "${root_dir}"
  if [[ ! -e "${root_dir}/android" ]]; then
    ln -s "${repo_dir}/android" "${root_dir}/android"
  fi

  make --no-print-directory -f "${repo_dir}/Include.mk" clean build \
    ROOT_DIR="${root_dir}" \
    GO_SRC="${repo_dir}/tests/fixtures/${fixture}" \
    APP_NAME="Builder ${fixture} fixture" \
    APP_ID="${app_id}" \
    SCREEN_ORIENTATION=landscape \
    ANDROID_TARGET="${android_target}" \
    VERSION="${version}" \
    USES_CLEARTEXT_TRAFFIC="${cleartext}" \
    ENABLE_ON_BACK_INVOKED_CALLBACK="${back_invoked}" \
    ALLOW_BACKUP="${allow_backup}" \
    APP_RES_DIR="${app_res_dir}" \
    NO_COLOR=1

  local android_dir="${root_dir}/.build/android"
  local aar="${android_dir}/app/libs/game.aar"
  local apk="${android_dir}/app/build/outputs/apk/debug/app-debug.apk"
  test -s "${aar}"
  test -s "${apk}"

  (
    cd "${android_dir}"
    ./gradlew -q --console=plain --warning-mode=all lintDebug lintRelease
  )

  "${build_tools}/zipalign" -c -P 16 4 "${apk}"
  "${build_tools}/apksigner" verify --verbose "${apk}" >/dev/null

  local inspect_dir="${build_parent}/inspect-${fixture}"
  rm -rf "${inspect_dir}"
  mkdir -p "${inspect_dir}/native"
  unzip -p "${aar}" classes.jar > "${inspect_dir}/classes.jar"
  unzip -oq "${apk}" 'lib/*/*.so' -d "${inspect_dir}/native"

  local mobile_signature
  mobile_signature="$(
    javap -public -classpath "${inspect_dir}/classes.jar" \
      "${app_id}.corelib.mobile.Mobile"
  )"
  case "${fixture}" in
    legacy|ime)
      grep -Fq 'public static native void setAndroidID(long);' <<<"${mobile_signature}"
      grep -Fq 'public static native void setTimezone(java.lang.String);' <<<"${mobile_signature}"
      if grep -Fq 'registerAndroidBridge' <<<"${mobile_signature}"; then
        echo "${fixture} fixture unexpectedly exports AndroidBridge" >&2
        exit 1
      fi
      if grep -Fq 'registerBackBridge' <<<"${mobile_signature}"; then
        echo "${fixture} fixture unexpectedly exports BackBridge" >&2
        exit 1
      fi
      if grep -Fq 'registerFilePickerBridge' <<<"${mobile_signature}"; then
        echo "${fixture} fixture unexpectedly exports FilePickerBridge" >&2
        exit 1
      fi
      ;;
    bridge)
      grep -Fq 'registerAndroidBridge' <<<"${mobile_signature}"
      if grep -Fq 'setAndroidID' <<<"${mobile_signature}"; then
        echo "bridge fixture unexpectedly depends on legacy setAndroidID" >&2
        exit 1
      fi

      local bridge_signature
      bridge_signature="$(
        javap -public -classpath "${inspect_dir}/classes.jar" \
          "${app_id}.corelib.mobile.AndroidBridge"
      )"
      test "$(grep -Ec '^  public abstract ' <<<"${bridge_signature}")" -eq 21
      grep -Fq 'public abstract java.lang.String androidID() throws java.lang.Exception;' \
        <<<"${bridge_signature}"
      grep -Fq 'public abstract int sdkInt();' <<<"${bridge_signature}"
      grep -Fq 'public abstract java.lang.String localIPAddresses() throws java.lang.Exception;' \
        <<<"${bridge_signature}"
      grep -Fq 'public abstract void restartApp() throws java.lang.Exception;' \
        <<<"${bridge_signature}"
      if grep -Fq 'registerFilePickerBridge' <<<"${mobile_signature}"; then
        echo "bridge fixture unexpectedly exports FilePickerBridge" >&2
        exit 1
      fi
      ;;
    back)
      grep -Fq 'registerBackBridge' <<<"${mobile_signature}"
      grep -Fq 'public static native void setAndroidID(long);' <<<"${mobile_signature}"

      local back_bridge_signature
      local back_handler_signature
      back_bridge_signature="$(
        javap -public -classpath "${inspect_dir}/classes.jar" \
          "${app_id}.corelib.mobile.BackBridge"
      )"
      back_handler_signature="$(
        javap -public -classpath "${inspect_dir}/classes.jar" \
          "${app_id}.corelib.mobile.BackHandler"
      )"
      grep -Fq 'public abstract void setHandler(' <<<"${back_bridge_signature}"
      grep -Fq '.corelib.mobile.BackHandler);' <<<"${back_bridge_signature}"
      grep -Fq 'public abstract boolean onBack();' <<<"${back_handler_signature}"
      if grep -Fq 'registerFilePickerBridge' <<<"${mobile_signature}"; then
        echo "back fixture unexpectedly exports FilePickerBridge" >&2
        exit 1
      fi
      ;;
    picker)
      grep -Fq 'public static native void setAndroidID(long);' <<<"${mobile_signature}"
      grep -Fq 'public static native void registerFilePickerBridge(' <<<"${mobile_signature}"
      if grep -Fq 'registerAndroidBridge' <<<"${mobile_signature}"; then
        echo "picker fixture unexpectedly depends on AndroidBridge" >&2
        exit 1
      fi
      if grep -Fq 'registerBackBridge' <<<"${mobile_signature}"; then
        echo "picker fixture unexpectedly depends on BackBridge" >&2
        exit 1
      fi

      local picker_bridge_signature
      picker_bridge_signature="$(
        javap -public -classpath "${inspect_dir}/classes.jar" \
          "${app_id}.corelib.mobile.FilePickerBridge"
      )"
      test "$(grep -Ec '^  public abstract ' <<<"${picker_bridge_signature}")" -eq 2
      grep -Fq 'public abstract void open(java.lang.String);' <<<"${picker_bridge_signature}"
      grep -Fq 'public abstract void setHandler(' <<<"${picker_bridge_signature}"

      local picker_handler_signature
      picker_handler_signature="$(
        javap -public -classpath "${inspect_dir}/classes.jar" \
          "${app_id}.corelib.mobile.FilePickerHandler"
      )"
      test "$(grep -Ec '^  public abstract ' <<<"${picker_handler_signature}")" -eq 1
      grep -Fq 'public abstract void onResult(java.lang.String, java.lang.String);' \
        <<<"${picker_handler_signature}"
      ;;
    *)
      echo "unknown Android fixture ${fixture}" >&2
      exit 2
      ;;
  esac

  if [[ "${fixture}" == ime ]]; then
    grep -Fq 'registerIMEBridge' <<<"${mobile_signature}"
    local ime_signature
    ime_signature="$(
      javap -public -classpath "${inspect_dir}/classes.jar" \
        "${app_id}.corelib.mobile.IMEBridge"
    )"
    test "$(grep -Ec '^  public abstract ' <<<"${ime_signature}")" -eq 3
    grep -Fq 'public abstract void show(int, int);' <<<"${ime_signature}"
    grep -Fq 'public abstract java.lang.String composing();' <<<"${ime_signature}"
    grep -Fq 'public abstract void hide();' <<<"${ime_signature}"
  fi

  local manifest
  manifest="$("${android_sdk_root}/cmdline-tools/latest/bin/apkanalyzer" manifest print "${apk}")"
  test "$("${android_sdk_root}/cmdline-tools/latest/bin/apkanalyzer" manifest version-code "${apk}")" \
    = "${expected_version_code}"
  grep -Fq 'android.permission.ACCESS_NETWORK_STATE' <<<"${manifest}"
  grep -Fq 'android:name="games.example.builder.'"${fixture}"'.ProcessRestartActivity"' \
    <<<"${manifest}"
  grep -Fq 'android:exported="false"' <<<"${manifest}"
  grep -Fq 'android:process=":restart"' <<<"${manifest}"
  grep -Fq "android:allowBackup=\"${allow_backup}\"" <<<"${manifest}"
  if [[ "${cleartext}" == true ]]; then
    grep -Fq 'android:usesCleartextTraffic="true"' <<<"${manifest}"
  elif grep -Fq 'android:usesCleartextTraffic=' <<<"${manifest}"; then
    echo "${fixture} APK unexpectedly overrides Android cleartext policy" >&2
    exit 1
  fi
  if [[ "${back_invoked}" == true ]]; then
    grep -Fq 'android:enableOnBackInvokedCallback="true"' <<<"${manifest}"
  elif grep -Fq 'android:enableOnBackInvokedCallback=' <<<"${manifest}"; then
    echo "${fixture} APK unexpectedly overrides Android Back dispatch policy" >&2
    exit 1
  fi

  local shared_object
  local shared_object_count=0
  while IFS= read -r -d '' shared_object; do
    shared_object_count=$((shared_object_count + 1))
    while IFS= read -r alignment; do
      if (( alignment < 0x4000 )); then
        echo "native LOAD segment is not 16 KiB aligned: ${shared_object}" >&2
        exit 1
      fi
    done < <("${ndk_tools}/llvm-readelf" -lW "${shared_object}" | awk '$1 == "LOAD" { print $NF }')
  done < <(find "${inspect_dir}/native" -type f -name '*.so' -print0)
  test "${shared_object_count}" -gt 0

  echo "build-android-fixtures: ${fixture} (${android_target}) passed"
}

build_fixture legacy v0.8.9 "" "" true 80900 "${repo_dir}/tests/fixtures/resources"
build_fixture bridge v1.0.2 true "" false 1000200
build_fixture back v1.0.3 "" true true 1000300
build_fixture picker v1.0.4 "" "" true 1000400
build_fixture ime v1.0.5 "" "" true 1000500
