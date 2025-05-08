#!/bin/bash
source ../multiNodeUtils.sh

# Argument parsing
if [ "$1" = "jacoco" ]; then
  JACOCO_ENABLED=true
else
  JACOCO_ENABLED=false
fi

# Clean out any old sandbox, make a new one
OUTDIR=sandbox
rm -fr $OUTDIR; mkdir -p $OUTDIR

# Check for os
SEP=:
case "$(uname)" in
  CYGWIN*) SEP=";" ;;
esac

# Resolve JVM classpath
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

JVM_CLASSPATH="$PROJECT_ROOT/h2o-core/build/classes/java/main"
JVM_CLASSPATH+=":$PROJECT_ROOT/h2o-core/build/resources/main"
JVM_CLASSPATH+=":$PROJECT_ROOT/h2o-test-support/build/classes/java/main"
JVM_CLASSPATH+=":$PROJECT_ROOT/h2o-test-support/build/resources/main"
JVM_CLASSPATH+=":$PROJECT_ROOT/h2o-admissibleml/build/classes/java/main"
JVM_CLASSPATH+=":$PROJECT_ROOT/h2o-admissibleml/build/resources/main"

# Include any jar files from typical build outputs
for jar in $(find "$PROJECT_ROOT/build/libs" "$PROJECT_ROOT/h2o-assemblies/build/libs" -name "*.jar" 2>/dev/null); do
  JVM_CLASSPATH+=":$jar"
done

# Run cleanup on interrupt or exit
function cleanup () {
  kill -9 ${PID_1} ${PID_2} ${PID_3} ${PID_4} 1> /dev/null 2>&1
  wait 1> /dev/null 2>&1
  RC=$(cat $OUTDIR/status.0)
  if [ $RC -ne 0 ]; then
    cat $OUTDIR/out.0
    echo "h2o-core junit tests FAILED"
  else
    echo "h2o-core junit tests PASSED"
  fi
  exit $RC
}
trap cleanup SIGTERM SIGINT

# Find java command
if [ -z "$TEST_JAVA_HOME" ]; then
  JAVA_CMD="java"
else
  JAVA_CMD="$TEST_JAVA_HOME/bin/java"
fi

# Set memory
MAX_MEM=${H2O_JVM_XMX:-3g}

# Jacoco support
if [ $JACOCO_ENABLED = true ]; then
  AGENT="../jacoco/jacocoagent.jar"
  COVERAGE="-javaagent:$AGENT=destfile=build/jacoco/h2o-core_multi.exec"
  MAX_MEM=${H2O_JVM_XMX:-8g}
else
  COVERAGE=""
fi

# JVM command with required --add-opens
JVM="nice $JAVA_CMD $COVERAGE \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Xmx${MAX_MEM} -Xms${MAX_MEM} -ea \
  -cp ${JVM_CLASSPATH} \
  -Dsys.ai.h2o.rapids.checkObjectConsistency=true \
  -Dsys.ai.h2o.activeProcessorCount=4 \
  ${ADDITIONAL_TEST_JVM_OPTS}"

echo "$JVM" > $OUTDIR/jvm_cmd.txt

# Tests to run
JUNIT_TESTS_BOOT="water.AAA_PreCloudLock"
JUNIT_TESTS_SLOW="water.parser.ParseProgressTest\|water.fvec.WordCountBigTest"
JUNIT_RUNNER="water.junit.H2OTestRunner"

(cd src/test/java; find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') \
  | grep -v $JUNIT_TESTS_SLOW | grep -v $JUNIT_TESTS_BOOT | sort > $OUTDIR/all_tests.txt

set -f # Disable globbing
DOONLY="${DOONLY:-.*}"
IGNORE="${IGNORE:-thisstringwillnotoccur}"

grep -v "$IGNORE" $OUTDIR/all_tests.txt > $OUTDIR/tests.not_ignored.txt
grep "$DOONLY" $OUTDIR/tests.not_ignored.txt > $OUTDIR/tests.txt
set +f

# Start 4 helper JVMs (nodes)
CLUSTER_NAME=junit_cluster_$$
CLUSTER_BASEPORT=43000
runCluster

# Jacoco flag
if [ $JACOCO_ENABLED = true ]; then
  JACOCO_FLAG="-Dtest.jacocoEnabled=true"
else
  JACOCO_FLAG=""
fi

# Run test runner
echo "Running h2o-core junit tests..."
($JVM $TEST_SSL \
  -Dbuild.id=$BUILD_ID \
  -Djob.name=$JOB_NAME \
  -Dgit.commit=$GIT_COMMIT \
  -Dgit.branch=$GIT_BRANCH \
  -Dai.h2o.name=$CLUSTER_NAME \
  -Dai.h2o.ip=$H2O_NODE_IP \
  -Dai.h2o.baseport=$CLUSTER_BASEPORT \
  -Dai.h2o.ga_opt_out=yes \
  $JACOCO_FLAG \
  $JUNIT_RUNNER \
  $JUNIT_TESTS_BOOT \
  $(cat $OUTDIR/tests.txt) ; echo $? > $OUTDIR/status.0) > $OUTDIR/out.0 2>&1

grep EXECUTION $OUTDIR/out.0 | sed -e "s/.*TEST \(.*\) EXECUTION TIME: \(.*\) (Wall.*/\2 \1/" | sort -gr | head -n 10 >> $OUTDIR/out.0

cleanup
