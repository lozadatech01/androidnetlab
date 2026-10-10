#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/new/app"
GRADLE="$APP/build.gradle"
MAIN="$APP/src/main/java/dev/lozadatech/androidnetlab/next/MainActivity.kt"
RUNTIME="$APP/src/main/java/dev/lozadatech/androidnetlab/next/runtime/QemuRuntime.kt"
EXPECTED_PACKAGE="dev.lozadatech.androidnetlab.next"

fail() { echo "PACKAGE CHECK FAILED: $*" >&2; exit 1; }

grep -Fq "namespace '$EXPECTED_PACKAGE'" "$GRADLE" || fail "Gradle namespace changed unexpectedly"
grep -Fq "applicationId '$EXPECTED_PACKAGE'" "$GRADLE" || fail "applicationId changed unexpectedly"
grep -Fq "package $EXPECTED_PACKAGE" "$MAIN" || fail "MainActivity package declaration is missing or changed"
grep -Fq "package $EXPECTED_PACKAGE.runtime" "$RUNTIME" || fail "QemuRuntime must stay in the isolated runtime subpackage"

# The active app source must not define duplicate MainActivity classes.
count="$(grep -R --include='*.kt' --include='*.java' -E 'class MainActivity([[:space:]]|\{|\()' "$APP/src/main" | wc -l | tr -d ' ')"
[[ "$count" == "1" ]] || fail "Expected exactly one MainActivity in active app source; found $count"

echo "Package boundaries verified: $EXPECTED_PACKAGE (runtime subpackage isolated)."
