#!/bin/bash

set -e

MODE="$1"

if [[ -z "$MODE" ]]; then
  echo "MODE is required (e.g., MODE_NIGHTLY)"
  exit 1
fi

# Simulate loading pipeline context (can be extended)
echo "Initializing pipeline context for mode: $MODE"

# Export environment variables that would normally be set by Groovy pipelineContext
export PIPELINE_MODE="$MODE"
export BUILD_TIMESTAMP=$(date +%s)

# You can optionally write values to a file for later sourcing
cat <<EOF > pipeline.env
PIPELINE_MODE=$MODE
BUILD_TIMESTAMP=$BUILD_TIMESTAMP
EOF

exit 0
