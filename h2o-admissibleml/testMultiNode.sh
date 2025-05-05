#!/bin/bash
set -e
source ../multiNodeUtils.sh

# Argument parsing
if [ "$1" = "jacoco" ]; then
    JACOCO_ENABLED=true
else
    JACOCO_ENABLED=false
fi

# Clean out any old sandbox, make a new one
OUTDIR=sandbox/multi

MKDIR=mkdir
SEP=:
case "`uname`" in
    CYGWIN* )
      MKDIR=mkdir.exe
      SEP=";"
      ;;
esac
rm -fr $OUTDIR
$MKDIR -p $OUTDIR

function cleanup () {
  kill -9 ${PID_11} 1> /dev/null 2>&1
  kill -9 ${PID_12} 1> /dev/null 2>&1
  kill -9 ${PID_13} 1> /dev/null 2>&1
  wait 1> /dev/null 2>&1
  RC="`paste $OUTDIR/status.* | sed 's/[[:blank:]]//g'`"
  if [ "$RC" != "0" ]; then
    cat $OUTDIR/out.*
    echo h2o-admissibleml junit tests FAILED
    exit 1
  else
    echo h2o-admissibleml junit tests PASSED
    exit 0
  fi
}

trap cleanup SIGTERM SIGINT

# Find java command
if [ -z "$TEST_JAVA_HOME" ]; then
  JAVA_CMD="java"
else
  JAVA_CMD="$TEST_JAVA_HOME/bin/java"
  JAVA6_REGEXP=".*1\.6.*"
  if [[ $TEST_JAVA_HOME =~ $JAVA6_REGEXP ]]; then
    JAVA_CMD="${JAVA_CMD}"
  fi
fi

MAX_MEM=${H2O_JVM_XMX:-2500m}

# Check if coverage should be run
if [ $JACOCO_ENABLED = true ]; then
    AGENT="../jacoco/jacocoagent.jar"
    COVERAGE="-javaagent:$AGENT=destfile=build/jacoco/h2o-admissibleml.exec"
    MAX_MEM=${H2O_JVM_XMX:-8g}
else
    COVERAGE=""
fi

# Define classpath
JVM_CLASSPATH="build/classes/java/main${SEP}build/classes/java/test${SEP}build/resources/main"
JVM_CLASSPATH="${JVM_CLASSPATH}${SEP}../h2o-core/build/classes/java/main${SEP}../h2o-core/build/classes/java/test${SEP}../h2o-core/build/resources/main"
JVM_CLASSPATH="${JVM_CLASSPATH}${SEP}../h2o-test-support/build/classes/java/main${SEP}../h2o-test-support/build/classes/java/test${SEP}../h2o-test-support/build/resources/main"
JVM_CLASSPATH="${JVM_CLASSPATH}${SEP}../h2o-core/build/libs/*${SEP}../h2o-test-support/build/libs/*${SEP}../lib/*"

JVM="nice $JAVA_CMD $COVERAGE -ea -Xmx${MAX_MEM} -Xms${MAX_MEM} -DcloudSize=4 -cp ${JVM_CLASSPATH} ${ADDITIONAL_TEST_JVM_OPTS}"
echo "$JVM" > $OUTDIR/jvm_cmd.txt

# Prepare tests list
(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') | /usr/bin/sort > $OUTDIR/tests.txt

# Output ignored and doonly test configs
echo $IGNORE > $OUTDIR/tests.ignore.txt
echo $DOONLY > $OUTDIR/tests.doonly.txt

# Launch 3 helper JVMs
CLUSTER_NAME=junit_cluster_$$
CLUSTER_BASEPORT_1=44000
$JVM water.H2O -name $CLUSTER_NAME.1 -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT_1 -ga_opt_out $SSL 1> $OUTDIR/out.1.1 2>&1 & PID_11=$!
$JVM water.H2O -name $CLUSTER_NAME.1 -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT_1 -ga_opt_out $SSL 1> $OUTDIR/out.1.2 2>&1 & PID_12=$!
$JVM water.H2O -name $CLUSTER_NAME.1 -ip $H2O_NODE_IP -baseport $CLUSTER_BASEPORT_1 -ga_opt_out $SSL 1> $OUTDIR/out.1.3 2>&1 & PID_13=$!

# Handle JaCoCo flag
if [ $JACOCO_ENABLED = true ]; then
    JACOCO_FLAG="-Dtest.jacocoEnabled=true"
else
    JACOCO_FLAG=""
fi

# Run main test driver
echo Running h2o-admissibleml junit tests...
sleep 10
($JVM $TEST_SSL \
    -Ddoonly.tests=$DOONLY \
    -Dbuild.id=$BUILD_ID \
    -Dignore.tests=$IGNORE \
    -Djob.name=$JOB_NAME \
    -Dgit.commit=$GIT_COMMIT \
    -Dgit.branch=$GIT_BRANCH \
    -Dai.h2o.name=$CLUSTER_NAME.1 \
    -Dai.h2o.ip=$H2O_NODE_IP \
    -Dai.h2o.baseport=$CLUSTER_BASEPORT_1 \
    -Dai.h2o.ga_opt_out=yes \
    $JACOCO_FLAG \
    water.junit.H2OTestRunner $(cat $OUTDIR/tests.txt) 2>&1 ; echo $? > $OUTDIR/status.1) \
    1> $OUTDIR/out.1 2>&1 & PID_1=$!

wait ${PID_1} 1> /dev/null 2>&1
grep EXECUTION $OUTDIR/out.* | sed -e "s/.*TEST \(.*\) EXECUTION TIME: \(.*\) (Wall.*/\2 \1/" | sort -gr | head -n 10 >> $OUTDIR/out.0

cleanup
