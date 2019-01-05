#!/bin/bash
source ../multiNodeUtils.sh

# Argument parsing
if [ "$1" = "jacoco" ]
then
    JACOCO_ENABLED=true
else
    JACOCO_ENABLED=false
fi

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

# Memory should be explicitly kept to 2g. If the JVM runs out of
# memory on these tests, we need to diagnose the extra memory requirements
MAX_MEM=${H2O_JVM_XMX:-3g}

# Check if coverage should be run
if [ $JACOCO_ENABLED = true ]
then
    AGENT="../jacoco/jacocoagent.jar"
    COVERAGE="-javaagent:$AGENT=destfile=build/jacoco/h2o-core_multi.exec"
    MAX_MEM=${H2O_JVM_XMX:-8g}
else
    COVERAGE=""
fi
# Command to invoke test.
JVM="nice $JAVA_CMD $COVERAGE -Xmx${MAX_MEM} -Xms${MAX_MEM} -ea -cp ${JVM_CLASSPATH} ${ADDITIONAL_TEST_JVM_OPTS}"
echo "$JVM" > $OUTDIR/jvm_cmd.txt

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

(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') | grep -v $JUNIT_TESTS_SLOW | grep -v $JUNIT_TESTS_BOOT | /usr/bin/sort > $OUTDIR/all_tests.txt

set -f # no globbing
if [ foo"$DOONLY" = foo ]; then
   DOONLY=".*"
fi
if [ foo"$IGNORE" = foo ]; then
   IGNORE="thisstringwillnotoccur"
fi

# Output the comma-separated list of ignored/dooonly tests
# Ignored tests trump do-only tests
cat $OUTDIR/all_tests.txt | egrep -v "$IGNORE" > $OUTDIR/tests.not_ignored.txt
cat $OUTDIR/tests.not_ignored.txt | egrep "$DOONLY" > $OUTDIR/tests.txt
set +f

# Launch 4 helper JVMs.  All output redir'd at the OS level to sandbox files.
CLUSTER_NAME=junit_cluster_$$
CLUSTER_BASEPORT=43000
runCluster

# If coverage is being run, then pass a system variable flag so that timeout limits are increased.
if [ $JACOCO_ENABLED = true ]
then
    JACOCO_FLAG="-Dtest.jacocoEnabled=true"
else
    JACOCO_FLAG=""
fi

# Launch last driver JVM.  All output redir'd at the OS level to sandbox files.
echo Running h2o-core junit tests...
($JVM $TEST_SSL -Dbuild.id=$BUILD_ID -Djob.name=$JOB_NAME -Dgit.commit=$GIT_COMMIT -Dgit.branch=$GIT_BRANCH -Dai.h2o.name=$CLUSTER_NAME -Dai.h2o.baseport=$CLUSTER_BASEPORT -Dai.h2o.ga_opt_out=yes $JACOCO_FLAG $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt` 2>&1 ; echo $? > $OUTDIR/status.0) 1> $OUTDIR/out.0 2>&1

grep EXECUTION $OUTDIR/out.0 | sed -e "s/.*TEST \(.*\) EXECUTION TIME: \(.*\) (Wall.*/\2 \1/" | sort -gr | head -n 10 >> $OUTDIR/out.0

cleanup
