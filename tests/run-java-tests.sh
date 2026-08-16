#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_parent="${repo_dir}/.build/tests"
mkdir -p "${scratch_parent}"
scratch_dir="$(mktemp -d "${scratch_parent}/java.XXXXXX")"
trap 'rm -rf "${scratch_dir}"' EXIT

source_dir="${scratch_dir}/src/test/builder"
classes_dir="${scratch_dir}/classes"
mkdir -p "${source_dir}" "${classes_dir}"

perl -pe 's/\@\@APP_ID\@\@/test.builder/g' \
  "${repo_dir}/android/app/src/main/java/OptionalAndroidPlatform.java" \
  > "${source_dir}/OptionalAndroidPlatform.java"
cp "${repo_dir}/tests/java/OptionalAndroidPlatformTest.java" "${source_dir}/"

javac -Xlint:all -Werror -d "${classes_dir}" "${source_dir}"/*.java
java -ea -cp "${classes_dir}" test.builder.OptionalAndroidPlatformTest
