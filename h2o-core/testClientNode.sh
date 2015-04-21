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
  kill SIGKILL ${PID_1} ${PID_2} ${PID_3} ${PID_4}
  exit `cat $OUTDIR/status.0`
}
trap cleanup SIGTERM SIGINT

# Gradle puts files:
#   build/libs/h2o-core.jar      - Main h2o core classes
#   build/libs/test-h2o-core.jar - Test h2o core classes
#   build/resources/main         - Main resources (e.g. page.html)
JVM="nice java -ea -Xmx2g -Xms2g -cp build/classes/main${SEP}build/classes/test${SEP}../lib/*${SEP}../h2o-algos/build/classes/main${SEP}../h2o-app/build/classes/main${SEP}../h2o-genmodel/build/libs/h2o-genmodel.jar"

# Tests
# Must run first, before the cloud locks (because it tests cloud locking)
JUNIT_TESTS_BOOT="water.AAA_PreCloudLock"
# Too slow for normal junit runs
JUNIT_TESTS_SLOW="water.parser.ParseProgressTest\|water.fvec.WordCountBigTest"

# find all java in the src/test directory
# Cut the "./water/MRThrow.java" down to "water/MRThrow.java"
# Cut the   "water/MRThrow.java" down to "water/MRThrow"
# Slash/dot "water/MRThrow"      becomes "water.MRThrow"
(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') | grep -v $JUNIT_TESTS_SLOW | grep -v $JUNIT_TESTS_BOOT > $OUTDIR/tests.txt

# Launch 4 helper JVMs.  All output redir'd at the OS level to sandbox files.
$JVM water.H2OApp 1> $OUTDIR/out.1 2>&1 & PID_1=$!
$JVM water.H2OApp 1> $OUTDIR/out.2 2>&1 & PID_2=$!
$JVM water.H2OApp 1> $OUTDIR/out.3 2>&1 & PID_3=$!
#$JVM water.H2O 1> $OUTDIR/out.4 2>&1 & PID_4=$!

# Launch last driver JVM.  All output redir'd at the OS level to sandbox files,
# and tee'd to stdout so we can watch.
($JVM -Dh2o.arg.client=true org.junit.runner.JUnitCore water.ClientTest 2>&1 ; echo $? > $OUTDIR/status.0) | tee --append $OUTDIR/out.0 
($JVM -Dh2o.arg.client=true org.junit.runner.JUnitCore water.ClientTest 2>&1 ; echo $? > $OUTDIR/status.0) | tee --append $OUTDIR/out.0 
($JVM -Dh2o.arg.client=true org.junit.runner.JUnitCore water.ClientTest 2>&1 ; echo $? > $OUTDIR/status.0) | tee --append $OUTDIR/out.0 

cleanup
