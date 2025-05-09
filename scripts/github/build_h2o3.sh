#!/bin/bash
set -e

echo "Running H2O-3 build (mock)..."
# Simulate Gradle/Maven build
./gradlew --parallel build -x test -x :h2o-assemblies:minimal:shadowJar -x :h2o-assemblies:steam:shadowJar
