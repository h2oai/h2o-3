#!/bin/bash

set -e

# Simulate health check logic
# In the real Groovy script, this checks the Docker image, registry, etc.
# You can enhance this script with real docker/image/ping checks if needed

echo "Running simulated health check..."

# Example check: make sure Docker is working
docker info > /dev/null 2>&1
if [[ $? -ne 0 ]]; then
  echo "Docker is not available"
  exit 1
fi

echo "Health check passed."
exit 0
