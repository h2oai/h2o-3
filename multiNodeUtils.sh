#!/bin/bash
SSL=""
TEST_SSL=""
if [[ "$@" == "ssl" ]]; then
  if [ ! -f "../h2o-algos/src/test/resources/ssl.properties" ]; then
    SSL="-ssl_config ../../h2o-algos/src/test/resources/ssl2.properties"
    TEST_SSL="-Dai.h2o.ssl_config=../../h2o-algos/src/test/resources/ssl2.properties"
  else
    SSL="-ssl_config ../h2o-algos/src/test/resources/ssl.properties"
    TEST_SSL="-Dai.h2o.ssl_config=../h2o-algos/src/test/resources/ssl.properties"
  fi

fi

function runCluster () {
  $JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.1 2>&1 & PID_1=$!
  $JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.2 2>&1 & PID_2=$!
  $JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.3 2>&1 & PID_3=$!
  $JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.4 2>&1 & PID_4=$!
}