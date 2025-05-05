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

# Example: activate environment and run pytest, gradle, or any real test script
# Here we simulate running Python tests

python3 --version
java -version

# Replace this with real test commands
./gradlew testPy -Ppython.version=$PYTHON_VERSION --info
./gradlew testR --info
./gradlew testJava --info

exit 0
