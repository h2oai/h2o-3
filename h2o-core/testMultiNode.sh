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

# Run cleanup on interrupt or exit
function cleanup () {
  kill -9 ${PID_1} ${PID_2} ${PID_3} ${PID_4} 1> /dev/null 2>&1
  wait 1> /dev/null 2>&1
  RC=`cat $OUTDIR/status.0`
  if [ $RC -ne 0 ]; then
    cat $OUTDIR/out.0
    echo h2o-core junit tests FAILED
  else
    echo h2o-core junit tests PASSED
  fi
  exit $RC
}
trap cleanup SIGTERM SIGINT

# Gradle puts files:
#   build/libs/h2o-core.jar      - Main h2o core classes
#   build/libs/h2o-core-test.jar - Test h2o core classes
#   build/resources/main         - Main resources (e.g. page.html)
#JVM="nice java -ea -cp build/libs/h2o-core.jar${SEP}build/libs/h2o-core-test.jar${SEP}../lib/*"
# Ahhh... but the makefile runs the tests skipping the jar'ing step when possible.
# Also, sometimes see test files in the main-class directory, so put the test
# classpath before the main classpath.
# kbn: Rather than let it default heap to 1/4 of available dram,
# ece: use default heap size until PUBDEV-2142 resolved
# make it consistent for test. It's 2G is h2o-algos. So mimic that.
JVM="nice java -ea -cp build/classes/test${SEP}build/classes/main${SEP}../h2o-genmodel/build/libs/h2o-genmodel.jar${SEP}../lib/*"

# Tests
# Must run first, before the cloud locks (because it tests cloud locking)
JUNIT_TESTS_BOOT="water.AAA_PreCloudLock"
# Too slow for normal junit runs
JUNIT_TESTS_SLOW="water.parser.ParseProgressTest\|water.fvec.WordCountBigTest"

# Runner
# Default JUnit runner is org.junit.runner.JUnitCore
JUNIT_RUNNER="water.junit.H2OTestRunner"

# find all java in the src/test directory
# Cut the "./water/MRThrow.java" down to "water/MRThrow.java"
# Cut the   "water/MRThrow.java" down to "water/MRThrow"
# Slash/dot "water/MRThrow"      becomes "water.MRThrow"
# add 'sort' to get determinism on order of tests on different machines
# methods within a class can still reorder due to junit?
# '/usr/bin/sort' needed to avoid windows native sort when run in cygwin
(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') | grep -v $JUNIT_TESTS_SLOW | grep -v $JUNIT_TESTS_BOOT | /usr/bin/sort > $OUTDIR/tests.txt

# Launch 4 helper JVMs.  All output redir'd at the OS level to sandbox files.
CLUSTER_NAME=junit_cluster_$$
CLUSTER_BASEPORT=43000
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT --ga_opt_out 1> $OUTDIR/out.1 2>&1 & PID_1=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT --ga_opt_out 1> $OUTDIR/out.2 2>&1 & PID_2=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT --ga_opt_out 1> $OUTDIR/out.3 2>&1 & PID_3=$!
$JVM water.H2O -name $CLUSTER_NAME -baseport $CLUSTER_BASEPORT --ga_opt_out 1> $OUTDIR/out.4 2>&1 & PID_4=$!

# Launch last driver JVM.  All output redir'd at the OS level to sandbox files.
echo Running h2o-core junit tests...
($JVM -Dai.h2o.name=$CLUSTER_NAME -Dai.h2o.baseport=$CLUSTER_BASEPORT -Dai.h2o.ga_opt_out=yes $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt` 2>&1 ; echo $? > $OUTDIR/status.0) 1> $OUTDIR/out.0 2>&1

grep EXECUTION $OUTDIR/out.0 | cut "-d " -f22,19 | awk '{print $2 " " $1}'| sort -gr | head -n 10 >> $OUTDIR/out.0

cleanup
