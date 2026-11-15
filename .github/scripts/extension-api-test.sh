#!/usr/bin/env bash
set -euo pipefail
cd build/extension-api
sha256sum --check sha256sums.txt
for apk in host.apk tests.apk en-webnovel.apk en-azurechronicles.apk all-zlibrary.apk en-caleredhair.apk ar-galaxynovels.apk; do
  adb install -r -t "$apk"
done
adb shell am instrument -w -r \
  -e class io.grimoire.app.extension.ExtensionHostApiTest \
  -e extensionApiFixtures true \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee result.log
grep -Fq 'OK (4 tests)' result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' result.log)" -eq 4
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.data.local.ChapterCodecMigrationTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee codec-result.log
grep -Fq 'OK (4 tests)' codec-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' codec-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' codec-result.log)" -eq 4
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' codec-result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.data.local.LibraryStorageTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee library-result.log
grep -Fq 'OK (3 tests)' library-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' library-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' library-result.log)" -eq 3
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' library-result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.data.local.DownloadCommitTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee download-result.log
grep -Fq 'OK (9 tests)' download-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' download-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' download-result.log)" -eq 9
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' download-result.log; then exit 1; fi
