#!/bin/bash
set -e

echo "Running H2O-3 build (mock)..."
# Simulate Gradle/Maven build
./gradlew clean build -x test
