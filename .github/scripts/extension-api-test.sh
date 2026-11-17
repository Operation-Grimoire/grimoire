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

adb shell am instrument -w -r \
  -e class io.grimoire.app.ui.screen.reader.ReaderProjectionTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee projection-result.log
grep -Fq 'OK (4 tests)' projection-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' projection-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' projection-result.log)" -eq 4
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' projection-result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.data.local.ChapterObservationTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee observation-result.log
grep -Fq 'OK (6 tests)' observation-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' observation-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' observation-result.log)" -eq 6
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' observation-result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.data.local.ReadingCheckpointTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee checkpoint-result.log

grep -Fq 'OK (7 tests)' checkpoint-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' checkpoint-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' checkpoint-result.log)" -eq 7
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' checkpoint-result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.ui.screen.reader.ReaderCheckpointAdoptionTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee checkpoint-adoption-result.log

grep -Fq 'OK (4 tests)' checkpoint-adoption-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' checkpoint-adoption-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' checkpoint-adoption-result.log)" -eq 4
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' checkpoint-adoption-result.log; then exit 1; fi

adb shell am instrument -w -r \
  -e class io.grimoire.app.data.local.ReadingStateTest \
  io.grimoire.app.test/androidx.test.runner.AndroidJUnitRunner | tee reading-state-result.log

grep -Fq 'OK (22 tests)' reading-state-result.log
grep -Fq 'INSTRUMENTATION_CODE: -1' reading-state-result.log
test "$(grep -Ec '^INSTRUMENTATION_STATUS_CODE: 0[[:space:]]*$' reading-state-result.log)" -eq 22
if grep -Eq 'FAILURES|INSTRUMENTATION_(FAILED|ABORTED)|INSTRUMENTATION_STATUS_CODE: -[234]' reading-state-result.log; then exit 1; fi
