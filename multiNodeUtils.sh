#!/bin/bash
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

export IS_TEST_MULTI_NODE=true

if [[ "$(uname)" = "Darwin" ]]; then
  # Node discovery doesn't work on OS X for local interface
  export H2O_NODE_IP=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | grep -v " --> " | tail -n 1 | sed -E 's/.*inet ([^ ]+) .*/\1/')
else
  export H2O_NODE_IP=127.0.0.1
fi

function runCluster () {
  $JVM water.H2O -name $CLUSTER_NAME -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.1 2>&1 & PID_1=$!
  $JVM water.H2O -name $CLUSTER_NAME -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.2 2>&1 & PID_2=$!
  $JVM water.H2O -name $CLUSTER_NAME -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.3 2>&1 & PID_3=$!
  $JVM water.H2O -name $CLUSTER_NAME -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.4 2>&1 & PID_4=$!
}
