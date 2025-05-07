#!/bin/bash

set -e

MODE="$1"
PYTHON_VERSION="$2"
JAVA_VERSION="$3"

if [[ -z "$MODE" || -z "$PYTHON_VERSION" || -z "$JAVA_VERSION" ]]; then
  echo "Usage: $0 <MODE> <PYTHON_VERSION> <JAVA_VERSION>"
  exit 1
fi

echo "Running tests for mode: $MODE, Python: $PYTHON_VERSION, Java: $JAVA_VERSION"

# Confirm runtime versions
python3 --version
java -version

# Python tests
echo "::group::Run Python tests"
./gradlew --info :h2o-py:build
./gradlew --info :h2o-py:test
echo "::endgroup::"

# R tests
echo "::group::Run R tests"
./gradlew --info :h2o-r:test
echo "::endgroup::"

# Java unit tests
echo "::group::Run Java tests"
./gradlew --info test
echo "::endgroup::"

exit 0
