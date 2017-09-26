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
#export GRADLE_USER_HOME=$WORKSPACE
# Use the Jenkins-user shared R library; already sync'd no need to sync again
export R_LIBS_USER=${WORKSPACE}/../Rlibrary



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

# Use the Jenkins-user shared virtualenv
echo ""
echo "*********************************************"
echo "*  Activating Python virtualenv"
echo "*********************************************"
echo ""
source $WORKSPACE/../h2o_venv/bin/activate

which python
which pip

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
./gradlew build --no-daemon

