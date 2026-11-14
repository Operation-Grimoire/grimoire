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
