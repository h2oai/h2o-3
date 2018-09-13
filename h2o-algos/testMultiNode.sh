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
  kill -9 ${PID_11} ${PID_21} ${PID_31} ${PID_41} ${PID_51} 1> /dev/null 2>&1
  kill -9 ${PID_12} ${PID_22} ${PID_32} ${PID_42} ${PID_52} 1> /dev/null 2>&1
  kill -9 ${PID_13} ${PID_23} ${PID_33} ${PID_43} ${PID_53} 1> /dev/null 2>&1
  wait 1> /dev/null 2>&1
  RC="`paste $OUTDIR/status.* | sed 's/[[:blank:]]//g'`"
  if [ "$RC" != "00000" ]; then
    cat $OUTDIR/out.*
    echo h2o-algos junit tests FAILED
    exit 1
  else
    echo h2o-algos junit tests PASSED
    exit 0
  fi
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
# Gradle puts files:
#   build/classes/main - Main h2o core classes
#   build/classes/test - Test h2o core classes
#   build/resources/main - Main resources (e.g. page.html)

MAX_MEM=${H2O_JVM_XMX:-2500m}

# Check if coverage should be run
if [ $JACOCO_ENABLED = true ]
then
    AGENT="../jacoco/jacocoagent.jar"
    COVERAGE="-javaagent:$AGENT=destfile=build/jacoco/h2o-algos.exec"
    MAX_MEM=${H2O_JVM_XMX:-8g}
else
    COVERAGE=""
fi
JVM="nice $JAVA_CMD $COVERAGE -ea -Xmx${MAX_MEM} -Xms${MAX_MEM} -DcloudSize=4 -cp ${JVM_CLASSPATH} ${ADDITIONAL_TEST_JVM_OPTS}"
echo "$JVM" > $OUTDIR/jvm_cmd.txt
# Ahhh... but the makefile runs the tests skipping the jar'ing step when possible.
# Also, sometimes see test files in the main-class directory, so put the test
# classpath before the main classpath.
#JVM="nice java -ea -cp build/classes/test${SEP}build/classes/main${SEP}../h2o-core/build/classes/test${SEP}../h2o-core/build/classes/main${SEP}../lib/*"

# Tests
# Must run first, before the cloud locks (because it tests cloud locking)
JUNIT_TESTS_BOOT="hex.AAA_PreCloudLock"
JUNIT_TESTS_BIG="hex.word2vec.Word2VecTest"

# Runner
# Default JUnit runner is org.junit.runner.JUnitCore
JUNIT_RUNNER="water.junit.H2OTestRunner"

# find all java in the src/test directory
# Cut the "./water/MRThrow.java" down to "water/MRThrow.java"
# Cut the   "water/MRThrow.java" down to "water/MRThrow"
# Slash/dot "water/MRThrow"      becomes "water.MRThrow"

# On this h2o-algos testMultiNode.sh only, force the tests.txt to be in the same order for all machines.
# If sorted, the result of the cd/grep varies by machine. 
# If randomness is desired, replace sort with the unix 'shuf'
# Use /usr/bin/sort because of cygwin on windows. 
# Windows has sort.exe which you don't want. Fails? (is it a lineend issue)
(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') | grep -v $JUNIT_TESTS_BOOT | grep -v $JUNIT_TESTS_BIG | /usr/bin/sort > $OUTDIR/tests.txt

# Output the comma-separated list of ignored/dooonly tests
# Ignored tests trump do-only tests
echo $IGNORE > $OUTDIR/tests.ignore.txt
echo $DOONLY > $OUTDIR/tests.doonly.txt

# Launch 3 helper JVMs for each of the 5 parallel clouds.  All output redir'd at the OS level to sandbox files.
CLUSTER_NAME=junit_cluster_$$
CLUSTER_BASEPORT_1=44000
CLUSTER_BASEPORT_2=45000
CLUSTER_BASEPORT_3=46000
CLUSTER_BASEPORT_4=47000
CLUSTER_BASEPORT_5=48000
$JVM water.H2O -name $CLUSTER_NAME.1 -baseport $CLUSTER_BASEPORT_1 -ga_opt_out $SSL 1> $OUTDIR/out.1.1 2>&1 & PID_11=$!
$JVM water.H2O -name $CLUSTER_NAME.1 -baseport $CLUSTER_BASEPORT_1 -ga_opt_out $SSL 1> $OUTDIR/out.1.2 2>&1 & PID_12=$!
$JVM water.H2O -name $CLUSTER_NAME.1 -baseport $CLUSTER_BASEPORT_1 -ga_opt_out $SSL 1> $OUTDIR/out.1.3 2>&1 & PID_13=$!
$JVM water.H2O -name $CLUSTER_NAME.2 -baseport $CLUSTER_BASEPORT_2 -ga_opt_out $SSL 1> $OUTDIR/out.2.1 2>&1 & PID_21=$!
$JVM water.H2O -name $CLUSTER_NAME.2 -baseport $CLUSTER_BASEPORT_2 -ga_opt_out $SSL 1> $OUTDIR/out.2.2 2>&1 & PID_22=$!
$JVM water.H2O -name $CLUSTER_NAME.2 -baseport $CLUSTER_BASEPORT_2 -ga_opt_out $SSL 1> $OUTDIR/out.2.3 2>&1 & PID_23=$!
$JVM water.H2O -name $CLUSTER_NAME.3 -baseport $CLUSTER_BASEPORT_3 -ga_opt_out $SSL 1> $OUTDIR/out.3.1 2>&1 & PID_31=$!
$JVM water.H2O -name $CLUSTER_NAME.3 -baseport $CLUSTER_BASEPORT_3 -ga_opt_out $SSL 1> $OUTDIR/out.3.2 2>&1 & PID_32=$!
$JVM water.H2O -name $CLUSTER_NAME.3 -baseport $CLUSTER_BASEPORT_3 -ga_opt_out $SSL 1> $OUTDIR/out.3.3 2>&1 & PID_33=$!
$JVM water.H2O -name $CLUSTER_NAME.4 -baseport $CLUSTER_BASEPORT_4 -ga_opt_out $SSL 1> $OUTDIR/out.4.1 2>&1 & PID_41=$!
$JVM water.H2O -name $CLUSTER_NAME.4 -baseport $CLUSTER_BASEPORT_4 -ga_opt_out $SSL 1> $OUTDIR/out.4.2 2>&1 & PID_42=$!
$JVM water.H2O -name $CLUSTER_NAME.4 -baseport $CLUSTER_BASEPORT_4 -ga_opt_out $SSL 1> $OUTDIR/out.4.3 2>&1 & PID_43=$!
$JVM water.H2O -name $CLUSTER_NAME.5 -baseport $CLUSTER_BASEPORT_5 -ga_opt_out $SSL 1> $OUTDIR/out.5.1 2>&1 & PID_51=$!
$JVM water.H2O -name $CLUSTER_NAME.5 -baseport $CLUSTER_BASEPORT_5 -ga_opt_out $SSL 1> $OUTDIR/out.5.2 2>&1 & PID_52=$!
$JVM water.H2O -name $CLUSTER_NAME.5 -baseport $CLUSTER_BASEPORT_5 -ga_opt_out $SSL 1> $OUTDIR/out.5.3 2>&1 & PID_53=$!

# If coverage is being run, then pass a system variable flag so that timeout limits are increased.
if [ $JACOCO_ENABLED = true ]
then
    JACOCO_FLAG="-Dtest.jacocoEnabled=true"
else
    JACOCO_FLAG=""
fi

# Launch last driver JVM.  All output redir'd at the OS level to sandbox files.
echo Running h2o-algos junit tests...

sleep 10

($JVM $TEST_SSL -Ddoonly.tests=$DOONLY -Dbuild.id=$BUILD_ID -Dignore.tests=$IGNORE -Djob.name=$JOB_NAME -Dgit.commit=$GIT_COMMIT -Dgit.branch=$GIT_BRANCH -Dai.h2o.name=$CLUSTER_NAME.1 -Dai.h2o.baseport=$CLUSTER_BASEPORT_1 -Dai.h2o.ga_opt_out=yes $JACOCO_FLAG $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt | awk 'NR%5==0'` 2>&1 ; echo $? > $OUTDIR/status.1) 1> $OUTDIR/out.1 2>&1 & PID_1=$!
($JVM $TEST_SSL -Ddoonly.tests=$DOONLY -Dbuild.id=$BUILD_ID -Dignore.tests=$IGNORE -Djob.name=$JOB_NAME -Dgit.commit=$GIT_COMMIT -Dgit.branch=$GIT_BRANCH -Dai.h2o.name=$CLUSTER_NAME.2 -Dai.h2o.baseport=$CLUSTER_BASEPORT_2 -Dai.h2o.ga_opt_out=yes $JACOCO_FLAG $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt | awk 'NR%5==1'` 2>&1 ; echo $? > $OUTDIR/status.2) 1> $OUTDIR/out.2 2>&1 & PID_2=$!
($JVM $TEST_SSL -Ddoonly.tests=$DOONLY -Dbuild.id=$BUILD_ID -Dignore.tests=$IGNORE -Djob.name=$JOB_NAME -Dgit.commit=$GIT_COMMIT -Dgit.branch=$GIT_BRANCH -Dai.h2o.name=$CLUSTER_NAME.3 -Dai.h2o.baseport=$CLUSTER_BASEPORT_3 -Dai.h2o.ga_opt_out=yes $JACOCO_FLAG $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt | awk 'NR%5==2'` 2>&1 ; echo $? > $OUTDIR/status.3) 1> $OUTDIR/out.3 2>&1 & PID_3=$!
($JVM $TEST_SSL -Ddoonly.tests=$DOONLY -Dbuild.id=$BUILD_ID -Dignore.tests=$IGNORE -Djob.name=$JOB_NAME -Dgit.commit=$GIT_COMMIT -Dgit.branch=$GIT_BRANCH -Dai.h2o.name=$CLUSTER_NAME.4 -Dai.h2o.baseport=$CLUSTER_BASEPORT_4 -Dai.h2o.ga_opt_out=yes $JACOCO_FLAG $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt | awk 'NR%5==3'` 2>&1 ; echo $? > $OUTDIR/status.4) 1> $OUTDIR/out.4 2>&1 & PID_4=$!
($JVM $TEST_SSL -Ddoonly.tests=$DOONLY -Dbuild.id=$BUILD_ID -Dignore.tests=$IGNORE -Djob.name=$JOB_NAME -Dgit.commit=$GIT_COMMIT -Dgit.branch=$GIT_BRANCH -Dai.h2o.name=$CLUSTER_NAME.5 -Dai.h2o.baseport=$CLUSTER_BASEPORT_5 -Dai.h2o.ga_opt_out=yes $JACOCO_FLAG $JUNIT_RUNNER $JUNIT_TESTS_BOOT `cat $OUTDIR/tests.txt | awk 'NR%5==4'` 2>&1 ; echo $? > $OUTDIR/status.5) 1> $OUTDIR/out.5 2>&1 & PID_5=$!

wait ${PID_1} ${PID_2} ${PID_3} ${PID_4} ${PID_5} 1> /dev/null 2>&1
grep EXECUTION $OUTDIR/out.* | sed -e "s/.*TEST \(.*\) EXECUTION TIME: \(.*\) (Wall.*/\2 \1/" | sort -gr | head -n 10 >> $OUTDIR/out.0

cleanup
