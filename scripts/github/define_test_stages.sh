#!/bin/bash

set -euo pipefail

MODE="$1"
PYTHON_VERSION="$2"
JAVA_VERSION="$3"
GROUP="${4:-all}"  # Optional, defaults to "all"

if [[ -z "$MODE" || -z "$PYTHON_VERSION" || -z "$JAVA_VERSION" ]]; then
  echo "Usage: $0 <MODE> <PYTHON_VERSION> <JAVA_VERSION> [GROUP]"
  exit 1
fi

echo "Running tests for:"
echo "- Mode         : $MODE"
echo "- Python       : $PYTHON_VERSION"
echo "- Java         : $JAVA_VERSION"
echo "- Group        : $GROUP"

# Show versions
echo "::group::Runtime Versions"
python3 --version
java -version
./gradlew --version
echo "::endgroup::"

# Group 1: Python tests
if [[ "$GROUP" == "1" || "$GROUP" == "all" ]]; then
  echo "::group::Run Python tests"
  ./gradlew --info :h2o-py:build
  ./gradlew --info :h2o-py:test
  echo "::endgroup::"
fi

# Group 2: R tests
if [[ "$GROUP" == "2" || "$GROUP" == "all" ]]; then
  echo "::group::Run R tests"
  ./gradlew --info :h2o-r:test
  echo "::endgroup::"
fi

# Group 3: Java unit tests
if [[ "$GROUP" == "3" || "$GROUP" == "all" ]]; then
  echo "::group::Run Java tests"
  ./gradlew --info test --continue
  echo "::endgroup::"
fi

# Optional: Split this into another group if needed
# echo "::group::Run Java Multi-node tests"
# ./gradlew --info :h2o-admissibleml:testMultiNode
# echo "::endgroup::"

echo "✅ Test group '$GROUP' completed for MODE=$MODE"
