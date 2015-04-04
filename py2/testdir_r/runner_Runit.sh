#/bin/sh

# takes a -n argument to disable the s3 download for faster testing

# Ensure that all your children are truly dead when you yourself are killed.
# http://www.davidpashley.com/articles/writing-robust-shell-scripts/#id2382181
# trap "kill -- -$BASHPID" INT TERM EXIT
# leave out EXIT for now
trap "kill -- -$BASHPID" INT TERM
echo "BASHPID: $BASHPID"
echo "current PID: $$"

set -o pipefail  # trace ERR through pipes
set -o errtrace  # trace ERR through 'time command' and other functions
set -o nounset   ## set -u : exit the script if you try to use an uninitialised variable
set -o errexit   ## set -e : exit the script if any statement returns a non-true return value

# remove any test*xml or TEST*xml in the current dir
rm -f test.*xml

# This gets the h2o.jar
# UPDATE: assume you did a build outside of this
source ./runner_setup.sh "$@"

rm -f h2o-nodes.json
python ../four_hour_cloud.py -cj pytest_config-jenkins.json &

# make sure this matches what's in the json!
CLOUD_IP=127.0.0.1
CLOUD_PORT=54474

CLOUD_PID=$!
jobs -l

echo ""
echo "Have to wait until h2o-nodes.json is available from the cloud build. Deleted it above."
echo "spin loop here waiting for it. Since the h2o.jar copy slows each node creation"
echo "it might be 12 secs per node"

while [ ! -f ./h2o-nodes.json ]
do
  sleep 5
done
ls -lt ./h2o-nodes.json

# We now have the h2o-nodes.json, that means we started the jvms
# Shouldn't need to wait for h2o cloud here..
# the test should do the normal cloud-stabilize before it does anything.
# n0.doit uses nosetests so the xml gets created on completion. (n0.doit is a single test thing)

# A little '|| true' hack to make sure we don't fail out if this subtest fails
# test_c1_rel has 1 subtest

# This could be a runner, that loops thru a list of tests.

echo "If it exists, pytest_config-<username>.json in this dir will be used"
echo "i.e. pytest_config-jenkins.json"
echo "Used to run as 0xcust.., with multi-node targets (possibly)"

# apparently this doesn't work on mac? getting full pathname
H2O_R_HOME=`readlink -f ../../h2o-r/tests`

#******************************************************
## mySetup() {
    # we setup .Renviron and delete the old local library if it exists
    # then make the R_LIB_USERS dir
    # creates /tmp/libPaths.$USER.cmd

    # UPDATE: assume the h2o R package has been installed by the build process?
    ## rm -f /tmp/libPaths.$USER.cmd
    ## ./Rsetup.sh
    ## cmd="R -f /tmp/libPaths.$USER.cmd --args $CLOUD_IP:$CLOUD_PORT"
    ## echo "Running this cmd:"
    ## echo $cmd
    # it's gotten long now because of all the installs

    ## python ./sh2junit.py -name 'libPaths' -timeout 1800 -- $cmd
## }

myR() {
    # we change dir, but return it to what it was, on the return
    pushd .
    # these are hardwired in the config json used above for the cloud
    # CLOUD_IP=
    # CLOUD_PORT=
    # get_s3_jar.sh now downloads it. We need to tell anqi's wrapper where to find it.
    # with an environment variable
    if [[ -z $2 ]];
    then
        timeout=30 # default to 30
    else
        timeout=$2
    fi

    which R
    R --version

    # first test will cause an h2o package install?
    # this is where we downloaded to. 
    # notice no version number
    # ../../h2o-1.6.0.1/R/h2oWrapper_1.0.tar.gz

    echo "FIX!  we don't need H2OWrapperDir stuff any more???"
    # export H2OWrapperDir="$PWD/../../h2o-downloaded/R"
    # echo "H2OWrapperDir should be $H2OWrapperDir"
    # ls $H2OWrapperDir/h2o*.tar.gz

    # we want $1 used for -name below, to not have .R suffix
    # test paths are always relative to tests
    testDir=$(dirname $1)
    shdir=$H2O_R_HOME/$testDir
    testName=$(basename $1)
    echo $testName
    rScript=$testName
    echo $rScript
    echo "Will run this cmd in $shdir"
    # cmd="$H2O_R_HOME/run.py --wipe --usecloud $CLOUD_IP:$CLOUD_PORT --test $testName"
    cmd="R -f $rScript --args $CLOUD_IP:$CLOUD_PORT"
    echo $cmd

    # don't fail on errors, since we want to check the logs in case that has more info!
    set +e
    # executes the $cmd in the target dir, but the logs stay in sandbox here
    # -dir is optional
    python ./sh2junit.py -shdir $shdir -name $testName -timeout $timeout -- $cmd || true

    set -e
    popd
}

echo "Okay to run h2oWrapper.R every time for now"

#***********************************************************************
# This is the list of tests
#***********************************************************************
# UPDATE: no Setup
## mySetup 

# can be slow if it had to reinstall all packages?
# export H2OWrapperDir="$PWD/../../h2o-downloaded/R"

# FIX! if we assume we're always running with a local build, we shouldn't load from here
# echo "Showing the H2OWrapperDir env. variable. Is it .../../h2o-downloaded/R?"
# printenv | grep H2OWrapperDir

#autoGen RUnits

# UPDATE: the run.py should handle this now??
### myR ../../R/tests/Utils/runnerSetupPackage 300
# myR ../../R/tests/testdir_munging/histograms/runit_histograms 1200

# these are used to run a single test (from the command line -d -t)
# myR $TESTDIR/$TEST 300
rm -f -r ./myR.tests.include
# add the myR and the timeout to the tests (TESTDIR is from relative path from tests)
# sed -e 's!^!myR !' -e 's!$! 300!' < ./myR.tests > ./myR.tests.include
sed -e 's!^!myR !' -e 's!$! 300!' < ./$1 > ./myR.tests.include
source ./myR.tests.include

# airlines is failing summary. put it last
#myR $single/runit_libR_airlines 120
# If this one fals, fail this script so the bash dies 
# We don't want to hang waiting for the cloud to terminate.
# produces xml too!
echo 'Shut down H2O'
curl -X POST $CLOUD_IP:$CLOUD_PORT/3/Shutdown.html > /dev/null

#***********************************************************************
# End of list of tests
#***********************************************************************

if ps -p $CLOUD_PID > /dev/null
then
    echo "$CLOUD_PID is still running after shutdown. Will kill"
    kill $CLOUD_PID
fi
ps aux | grep four_hour_cloud

jobs -l
echo ""
echo "You can stop this jenkins job now if you want. It's all done"

