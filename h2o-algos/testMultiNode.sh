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
  kill -9 ${PID_1} ${PID_2} ${PID_3} ${PID_4} >> /dev/null
  exit `cat $OUTDIR/status.0`
}

trap cleanup SIGTERM SIGINT

# Gradle puts files:
#   build/classes/main - Main h2o core classes
#   build/classes/test - Test h2o core classes
#   build/resources/main - Main resources (e.g. page.html)
JVM="nice java -ea -cp build/libs/h2o-algos.jar${SEP}build/libs/h2o-algos-test.jar${SEP}../h2o-core/build/libs/h2o-core.jar${SEP}../h2o-core/build/libs/h2o-core-test.jar${SEP}../lib/*"

# find all java in the src/test directory
# Cut the "./water/MRThrow.java" down to "water/MRThrow.java"
# Cut the   "water/MRThrow.java" down to "water/MRThrow"
# Slash/dot "water/MRThrow"      becomes "water.MRThrow"
(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') > $OUTDIR/tests.txt

# Launch 4 helper JVMs.  All output redir'd at the OS level to sandbox files.
$JVM water.H2O 1> $OUTDIR/out.1 2>&1 & PID_1=$!
$JVM water.H2O 1> $OUTDIR/out.2 2>&1 & PID_2=$!
$JVM water.H2O 1> $OUTDIR/out.3 2>&1 & PID_3=$!
$JVM water.H2O 1> $OUTDIR/out.4 2>&1 & PID_4=$!

# Launch last driver JVM.  All output redir'd at the OS level to sandbox files,
# and tee'd to stdout so we can watch.
(sleep 1; $JVM org.junit.runner.JUnitCore `cat $OUTDIR/tests.txt` 2>&1 ; echo $? > $OUTDIR/status.0) | tee $OUTDIR/out.0 

cleanup

