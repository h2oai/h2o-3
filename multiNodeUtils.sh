#!/bin/bash

# --- SSL Configuration ---
SSL=""
TEST_SSL=""

if [[ "$@" == "ssl" ]]; then
  if [ ! -f "../h2o-algos/src/test/resources/ssl.properties" ]; then
    SSL="-internal_security_conf ../../h2o-algos/src/test/resources/ssl2.properties"
    TEST_SSL="-Dai.h2o.internal_security_conf=../../h2o-algos/src/test/resources/ssl2.properties"
  else
    SSL="-internal_security_conf ../h2o-algos/src/test/resources/ssl.properties"
    TEST_SSL="-Dai.h2o.internal_security_conf=../h2o-algos/src/test/resources/ssl.properties"
  fi
fi

# --- Determine IP address ---
if [[ "$(uname)" == "Darwin" ]]; then
  # On macOS, use `scutil` to determine IP from network interfaces
  export H2O_NODE_IP=$(scutil --nwi | grep address | sed 's/.*://' | tr -d ' ' | head -1)
else
  # Default to localhost on Linux
  export H2O_NODE_IP=127.0.0.1
fi

# --- Function to launch 4-node H2O cluster ---
function runCluster () {
  echo "Launching 4-node H2O cluster named '$CLUSTER_NAME' on IP $H2O_NODE_IP, baseport $CLUSTER_BASEPORT"

  $JVM water.H2O -name "$CLUSTER_NAME" -ip "$H2O_NODE_IP" -baseport "$CLUSTER_BASEPORT" -ga_opt_out $SSL 1> "$OUTDIR/out.1" 2>&1 & PID_1=$!
  $JVM water.H2O -name "$CLUSTER_NAME" -ip "$H2O_NODE_IP" -baseport "$CLUSTER_BASEPORT" -ga_opt_out $SSL 1> "$OUTDIR/out.2" 2>&1 & PID_2=$!
  $JVM water.H2O -name "$CLUSTER_NAME" -ip "$H2O_NODE_IP" -baseport "$CLUSTER_BASEPORT" -ga_opt_out $SSL 1> "$OUTDIR/out.3" 2>&1 & PID_3=$!
  $JVM water.H2O -name "$CLUSTER_NAME" -ip "$H2O_NODE_IP" -baseport "$CLUSTER_BASEPORT" -ga_opt_out $SSL 1> "$OUTDIR/out.4" 2>&1 & PID_4=$!
}
