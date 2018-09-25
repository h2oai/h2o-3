#!/bin/bash

set -e
cat /proc/cpuinfo | grep 'model name' | head -n 1
#lsb_release -d
uname -r
BASE_PORT=$(eval ${BASE_PORT})
echo ${BASE_PORT}
echo ""
echo "************************************************************************************************************"
echo "job $JOB_NAME on node $NODE_NAME with EXECUTOR_NUMBER $EXECUTOR_NUMBER issued by jenkins $JENKINS_URL"
echo "************************************************************************************************************"
echo ""
date
pwd
echo "USING LOCAL BUILD OF $GIT_BRANCH from $GIT_URL"
echo "Building in $WORKSPACE on $NODE_NAME. Will make that the gradle home dir"
echo "Build tag is $BUILD_TAG"
echo "Build id is $BUILD_ID"
echo "Build url is $BUILD_URL"

echo "Debug ENV"

echo $PATH
env
locale

java -version
export BUILD_HADOOP=false
export GRADLE_USER_HOME=$WORKSPACE

echo ""
echo "*********************************************"
echo "*  Gradle clean"
echo "*********************************************"
echo ""
./gradlew clean
git clean -f

rm -f -r smalldata
ln -s /home/0xdiag/smalldata
rm -f -r bigdata
ln -s /home/0xdiag/bigdata

# Please note that this job is special
# It needs to pip install H2O, so it uses its own virtualenv
echo ""
echo "*********************************************"
echo "*  Activating Python virtualenv"
echo "*********************************************"
echo ""
virtualenv $WORKSPACE/h2o_venv --python=python2.7
source $WORKSPACE/h2o_venv/bin/activate
pip install --upgrade pip

# This should be done in gradle...
pip install numpy --upgrade
pip install scipy --upgrade
pip install -r h2o-py/requirements.txt

# Please note that this job is special
# It needs to install H2O for R, so it uses a local Rlibrary
# Copy libs from the Jenkins-user shared R library,
# then install H2O locally
mkdir -p ${WORKSPACE}/Rlibrary
rsync -rl ${WORKSPACE}/../Rlibrary/ ${WORKSPACE}/Rlibrary/
export R_LIBS_USER=${WORKSPACE}/Rlibrary


echo ""
echo "*********************************************"
echo "*  List Environment *"
echo "*********************************************"
echo ""
env
echo ""
echo "*********************************************"
echo "*  Building H2O"
echo "*********************************************"
echo ""
./gradlew build -x test
if [[ $(git diff) ]]; then
    echo
    echo "Non-zero git diff after running gradlew build:"
    echo
    git diff
    exit 1
fi

# Install R package to run these tests as a user would
echo ""
echo "*********************************************"
echo "*  Installing H2O R package"
echo "*********************************************"
echo ""
R CMD INSTALL h2o-r/R/src/contrib/h2o_*.*.*.*.tar.gz


echo ""
echo "*********************************************"
echo "*  INFO check test"
echo "*********************************************"
echo ""
echo "*  Run single runit test."
echo ""
cd h2o-r/tests
../../scripts/run.py --wipeall --test testdir_algos/deeplearning/runit_deeplearning_iris_basic.R --baseport $BASE_PORT --jvm.xmx 3g

echo ""
echo "*  Checking for INFO lines in output."
echo ""
cd results
# Grep gets exit code 1 if no match, but a no match is needed for success
set +e
grep -v INFO java_0_0.out.txt > INFO_file.txt
count=`wc -c INFO_file.txt | awk '{print $1}'`
if [ $count -gt 0 ]
then
  echo "There were non-INFO lines in the output."
  cat INFO_file.txt
  exit 1
else
  echo "Only INFO lines in output"
fi
set -e

echo ""
echo "*********************************************"
echo "*  Python-based initialization test"
echo "*********************************************"
echo ""
cd ${WORKSPACE}
/usr/bin/yes | pip uninstall h2o || true
pip install --force h2o-py/build/dist/*.whl

cd h2o-py/tests/testdir_jira
python h2o.init_test_HOQE-16.py
cd ../../..

echo ""
echo "*********************************************"
echo "*  R-based initialization test"
echo "*********************************************"
echo ""
cd h2o-r/tests/testdir_jira
R -f h2o.init_test_HOQE-16.R
