#!/bin/bash

set -euo pipefail

MODE="$1"
PYTHON_VERSION="$2"
JAVA_VERSION="$3"

if [[ -z "$MODE" || -z "$PYTHON_VERSION" || -z "$JAVA_VERSION" ]]; then
  echo "Usage: $0 <MODE> <PYTHON_VERSION> <JAVA_VERSION>"
  exit 1
fi

echo "Running tests for:"
echo "- Mode         : $MODE"
echo "- Python       : $PYTHON_VERSION"
echo "- Java         : $JAVA_VERSION"

# Show versions
echo "::group::Runtime Versions"
python3 --version
java -version
./gradlew --version
echo "::endgroup::"

# Run Python tests
echo "::group::Run Python tests"
./gradlew --info :h2o-py:build
./gradlew --info :h2o-py:test
echo "::endgroup::"

# Run R tests
echo "::group::Run R tests"
./gradlew --info :h2o-r:test
echo "::endgroup::"

# Run Java unit tests
echo "::group::Run Java tests"
./gradlew --info test --continue
echo "::endgroup::"

# Optional: Run Multi-node Java tests (if enabled)
# echo "::group::Run Java Multi-node tests"
# ./gradlew --info :h2o-admissibleml:testMultiNode
# echo "::endgroup::"

echo "✅ All applicable test stages completed for MODE=$MODE"
