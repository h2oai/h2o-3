#!/bin/bash

# Clean out any old sandbox, make a new one
OUTDIR=sandbox
rm -fr $OUTDIR; mkdir -p $OUTDIR

# Check for os
SEP=:
case "`uname`" in
    CYGWIN* )
      SEP=";"
      ;;
esac

function cleanup () {
  kill -9 ${PID_1} ${PID_2} ${PID_3} ${PID_4} 1> /dev/null 2>&1
  wait 1> /dev/null 2>&1
}

function countDataCells () {
  # Number of tokens we didn't find
  COUNT=0
  # Number of tokens we looked for
  TOTAL=0
  FILE=../smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv
  while IFS= read -r line; do
    IFS=',' read -r -a array <<< "$line"
    for el in "${array[@]}"; do
      # I don't check for "\d+" since things like "1" and "11" can appear as part of SSL encrypted gibberish
      # and it's not trivial to distinguish it from actual data
      if [[ ! $el =~ \"[0-9]+\" ]]; then
        grep -q -- "$el" sandbox/test.out
	    COUNT=$((COUNT + $?))
        TOTAL=$((TOTAL+1))
      fi
    done
  # Because the column names are mostly one letter they might actually appear
  # in the encrypted TCP gibberish so we'll skip them but check the actual data
  done <<< "$(sed 1d $FILE)"
  echo "Found $((TOTAL-COUNT)) tokens from a total of $TOTAL" 1>&2
  # Number of tokens we found
  echo $((TOTAL-COUNT))
}

function testOutput () {
  # Grab the nonSSL data field from the packet body in human readable format
  tshark -x -r $OUTDIR/h2o-nonSSL.pcap -T text | awk -F "  " '{print $3}' > $OUTDIR/test_tmp.out
  # Remove all newlines and spaces for future grep
  cat $OUTDIR/test_tmp.out | awk 1 RS='\n' ORS= | sed -e 's/ //g' > $OUTDIR/test.out

  # Check that all the data we used as input is in the TCP dump in not encrypted form!
  FOUND=$(countDataCells)
  if [[ $FOUND -eq 0 ]]; then
    echo "Haven't found any of the original data in the nonSSL TCP dump."
    echo h2o-algos junit tests FAILED
    exit 1
  fi

  # Grab the SSL data field from the packet body in human readable format
  tshark -x -r $OUTDIR/h2o-SSL.pcap -T text | awk -F "  " '{print $3}' > $OUTDIR/test_tmp.out
  cat $OUTDIR/test_tmp.out | awk 1 RS='\n' ORS= | sed -e 's/ //g' > $OUTDIR/test.out

  # Check that none of the data we used as input is in the TCP dump in notencrypted form!
  FOUND=$(countDataCells)
  if [[ $FOUND -ne 0 ]]; then
    echo "Found original data in the SSL TCP dump."
    echo h2o-algos junit tests FAILED
    exit 1
  fi

  echo h2o-algos junit tests PASSED
  exit 0
}

trap cleanup SIGTERM SIGINT

# Find java command
if [ -z "$TEST_JAVA_HOME" ]; then
  # Use default
  JAVA_CMD="java"
else
  # Use test java home
  JAVA_CMD="$TEST_JAVA_HOME/bin/java"
  # Increase XMX since JAVA_HOME can point to java6
  JAVA6_REGEXP=".*1\.6.*"
  if [[ $TEST_JAVA_HOME =~ $JAVA6_REGEXP ]]; then
    JAVA_CMD="${JAVA_CMD}"
  fi
fi

JVM="nice $JAVA_CMD -ea -Xmx3g -Xms3g -cp ${JVM_CLASSPATH} ${ADDITIONAL_TEST_JVM_OPTS}"
echo "$JVM" > $OUTDIR/jvm_cmd.txt

SSL=""
# Launch 3 helper JVMs.  All output redir'd at the OS level to sandbox files.
CLUSTER_NAME=junit_cluster_$$
CLUSTER_BASEPORT=44000
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.1 2>&1 & PID_1=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.2 2>&1 & PID_2=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.3 2>&1 & PID_3=$!

INTERFACE=${TSHARK_INTERFACE:-"eth0"}

echo Running nonSSL test on interface ${INTERFACE}...

pwd

tshark -i ${INTERFACE} -T fields -e data -w ${OUTDIR}/h2o-nonSSL.pcap 1> /dev/null 2>&1 & PID_4=$!

java -Dai.h2o.name=$CLUSTER_NAME -ea \
    -cp "build/libs/h2o-algos-test.jar${SEP}build/libs/h2o-algos.jar${SEP}../h2o-core/build/libs/h2o-core.jar${SEP}../h2o-core/build/libs/h2o-core-test.jar${SEP}../h2o-genmodel/build/libs/h2o-genmodel.jar${SEP}../lib/*" \
    water.network.SSLEncryptionTest

echo After test cleanup...

cleanup

SSL_CONFIG="src/test/resources/ssl.properties"
SSL="-internal_security_conf "$SSL_CONFIG
CLUSTER_NAME=$CLUSTER_NAME"_2"
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.1 2>&1 & PID_1=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.2 2>&1 & PID_2=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT -ga_opt_out $SSL 1> $OUTDIR/out.3 2>&1 & PID_3=$!

echo Running SSL test...

tshark -i ${INTERFACE} -T fields -e data -w ${OUTDIR}/h2o-SSL.pcap 1> /dev/null 2>&1 & PID_4=$!

java -Dai.h2o.name=$CLUSTER_NAME -ea \
    -cp "build/libs/h2o-algos-test.jar${SEP}build/libs/h2o-algos.jar${SEP}../h2o-core/build/libs/h2o-core.jar${SEP}../h2o-core/build/libs/h2o-core-test.jar${SEP}../h2o-genmodel/build/libs/h2o-genmodel.jar${SEP}../lib/*" \
    water.network.SSLEncryptionTest src/test/resources/ssl.properties

echo After test cleanup...

cleanup

testOutput
