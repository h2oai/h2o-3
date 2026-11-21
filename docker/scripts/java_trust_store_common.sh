#!/bin/bash

set -euo pipefail

readonly COMPROMISED_ALIASES=(
  'emsignrootcag1 [jdk]'
  'emsignrootcag2 [jdk]'
  'emsigneccrootcag3 [jdk]'
  'comodoaaaca [jdk]'
)

resolve_java() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_BIN="${JAVA_BIN:-${JAVA_HOME}/bin/java}"
    [[ -x "$JAVA_BIN" ]] || { echo "Java executable is not executable: $JAVA_BIN" >&2; return 1; }
    [[ -d "$JAVA_HOME" ]] || { echo "JAVA_HOME is not a directory: $JAVA_HOME" >&2; return 1; }
  else
    JAVA_BIN="${JAVA_BIN:-$(command -v java)}"
    [[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]] || { echo "Unable to resolve java executable" >&2; return 1; }
    JAVA_BIN="$(readlink -f "$JAVA_BIN")"
    JAVA_HOME="$($JAVA_BIN -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p' | head -n1)"
    if [[ -z "$JAVA_HOME" || ! -d "$JAVA_HOME" ]]; then
      JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    fi
  fi

  JAVA_KEYTOOL="${KEYTOOL:-${JAVA_HOME}/bin/keytool}"
  [[ -x "$JAVA_KEYTOOL" ]] || { echo "Java keytool does not belong to $JAVA_HOME: $JAVA_KEYTOOL" >&2; return 1; }

  local version
  version="$($JAVA_BIN -version 2>&1 | sed -n 's/^.*version "\([^"]*\)".*/\1/p' | head -n1)"
  [[ -n "$version" ]] || { echo "Unable to determine Java version from $JAVA_BIN" >&2; return 1; }
  JAVA_MAJOR="${version%%.*}"
  if [[ "$version" == 1.* ]]; then
    JAVA_MAJOR="${version#1.}"
    JAVA_MAJOR="${JAVA_MAJOR%%.*}"
  fi
  JAVA_MAJOR="${JAVA_MAJOR_OVERRIDE:-$JAVA_MAJOR}"

  if [[ -n "${JAVA_TRUST_STORE:-}" ]]; then
    JAVA_CACERTS="$JAVA_TRUST_STORE"
  elif [[ "$JAVA_MAJOR" == "8" ]]; then
    JAVA_CACERTS="${JAVA_HOME}/jre/lib/security/cacerts"
  else
    JAVA_CACERTS="${JAVA_HOME}/lib/security/cacerts"
  fi
  [[ -f "$JAVA_CACERTS" ]] || { echo "Java trust store does not exist: $JAVA_CACERTS" >&2; return 1; }
  [[ -r "$JAVA_CACERTS" ]] || { echo "Java trust store is not readable: $JAVA_CACERTS" >&2; return 1; }
}

trust_store_listing() {
  local password="${JAVA_TRUST_STORE_PASSWORD:-changeit}"
  "$JAVA_KEYTOOL" -list -keystore "$JAVA_CACERTS" -storepass "$password"
}
