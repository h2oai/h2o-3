#!/bin/bash

set -euo pipefail
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

JAVA_BIN_REAL="$(readlink -f "$(command -v java)")"
KEYTOOL_REAL="$(readlink -f "$(command -v keytool)")"
JAVA_HOME_REAL="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p' | head -n1)"

make_fixture() {
  local home="$1" store="$2"
  mkdir -p "$(dirname "$store")"
  local certificate="${store}.crt"
  keytool -genkeypair -alias fixture -keystore "$store" -storepass changeit -keypass changeit -dname CN=test -keyalg EC -validity 1 >/dev/null 2>&1
  keytool -exportcert -alias fixture -keystore "$store" -storepass changeit -rfc -file "$certificate" >/dev/null 2>&1
  for alias in 'emsignrootcag1 [jdk]' 'emsignrootcag2 [jdk]' 'emsigneccrootcag3 [jdk]' 'comodoaaaca [jdk]'; do
    keytool -importcert -alias "$alias" -keystore "$store" -storepass changeit -file "$certificate" -noprompt >/dev/null 2>&1
  done
  keytool -delete -alias fixture -keystore "$store" -storepass changeit -noprompt >/dev/null 2>&1
}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA8_HOME="$TEST_DIR/jdk8"
JAVA8_STORE="$JAVA8_HOME/jre/lib/security/cacerts"
make_fixture "$JAVA8_HOME" "$JAVA8_STORE"
JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA8_STORE" JAVA_MAJOR_OVERRIDE=8 bash "$SCRIPT_DIR/remediate_java_trust_store"
JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA8_STORE" JAVA_MAJOR_OVERRIDE=8 bash "$SCRIPT_DIR/remediate_java_trust_store"
JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA8_STORE" JAVA_MAJOR_OVERRIDE=8 bash "$SCRIPT_DIR/verify_java_trust_store"

JAVA11_HOME="$TEST_DIR/jdk11"
JAVA11_STORE="$JAVA11_HOME/lib/security/cacerts"
make_fixture "$JAVA11_HOME" "$JAVA11_STORE"
JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA11_STORE" JAVA_MAJOR_OVERRIDE=11 bash "$SCRIPT_DIR/remediate_java_trust_store"
JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA11_STORE" JAVA_MAJOR_OVERRIDE=11 bash "$SCRIPT_DIR/verify_java_trust_store"

if JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA11_STORE" JAVA_MAJOR_OVERRIDE=11 JAVA_TRUST_STORE_PASSWORD=wrong bash "$SCRIPT_DIR/verify_java_trust_store"; then
  echo 'Incorrect trust-store password unexpectedly succeeded' >&2
  exit 1
fi
if JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$TEST_DIR/missing/cacerts" JAVA_MAJOR_OVERRIDE=11 bash "$SCRIPT_DIR/verify_java_trust_store"; then
  echo 'Invalid trust-store path unexpectedly succeeded' >&2
  exit 1
fi

chmod 400 "$JAVA11_STORE"
if JAVA_HOME="$JAVA_HOME_REAL" JAVA_BIN="$JAVA_BIN_REAL" KEYTOOL="$KEYTOOL_REAL" JAVA_TRUST_STORE="$JAVA11_STORE" JAVA_MAJOR_OVERRIDE=11 bash "$SCRIPT_DIR/remediate_java_trust_store"; then
  echo 'Unwritable trust store unexpectedly succeeded' >&2
  exit 1
fi
chmod 600 "$JAVA11_STORE"

echo 'Java trust-store remediation tests passed'
