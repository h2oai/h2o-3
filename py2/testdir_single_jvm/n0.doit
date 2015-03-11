#!/bin/bash

echo
echo ======================================================================
echo ======================================================================
echo ======================================================================
echo
echo RUNNING $1
echo 
echo ======================================================================
echo 
date
time nosetests --verbose --stop --nocapture --nologcapture --with-xunit --xunit-file=$1.nosetests.xml --tests=$1
# time nosetests --verbose --stop --with-xunit --xunit-file=$1.nosetests.xml --tests=$1
echo 
date

# hack ..copy the log files we care about from the sandbox
# the sandbox could have been renamed for this run, and there could be multiple sandboxes in the job dir
# hmm. There's an environment variable H2O_SANDBOX_NAME that should be visible if it exists?

# assume this is run in the testdir_* directory
if [[ "$H2O_SANDBOX_NAME" == '' ]]; then
   SANDBOX='sandbox'
else
   SANDBOX=$H2O_SANDBOX_NAME
fi

# echo $SANDBOX

# only do the artifacts if you're jenkins
if [[ $USER != 'jenkins' ]]; then
    exit 0
fi

# if the directory doesn't exist complain
TESTBASE=$(basename "$1" .py)
ARCHIVE=$SANDBOX
ARCHIVE+="_"
ARCHIVE+=$TESTBASE

# remove the target copy directory if it exists
# echo $1
# echo $ARCHIVE
rm -f -r $ARCHIVE
mkdir $ARCHIVE

# warn if these don't exist?
if ls $SANDBOX/local* &> /dev/null; then
    cp $SANDBOX/commands.log $ARCHIVE
    cp -p $SANDBOX/local*stdout*log $ARCHIVE
    cp -p $SANDBOX/local*stderr*log $ARCHIVE
else 
    if ls $SANDBOX/remote* &> /dev/null; then
        cp -p $SANDBOX/commands.log $ARCHIVE
        cp -p $SANDBOX/remote*stdout*log $ARCHIVE
        cp -p $SANDBOX/remote*stderr*log $ARCHIVE
    else
        echo "Didn't find local or remote log files in dir $SANDBOX. No archiving done"
    fi
fi

# depends on the test
if [ -d "$ANDBOX/syn_datasets" ]; then
    cp -p $SANDBOX/syn_datasets $ARCHIVE
fi

# skip the done* and the ice directory

