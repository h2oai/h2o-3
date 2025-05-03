#!/bin/bash
set -e

MODE=$1
PYTHON_VERSION=$2
JAVA_VERSION=$3

echo "Running tests for MODE=${MODE}, Python=${PYTHON_VERSION}, Java=${JAVA_VERSION}"

# Add logic here to map MODE to actual test sets
make test-pyunit-small
