#!/bin/bash

set -e

if [ -z ${AWS_SSH_PRIVATE_KEY_FILE} ]
then
    echo "ERROR: You must set AWS_SSH_PRIVATE_KEY_FILE in the environment."
    exit 1
fi

# Adjust based on the build of H2O you want to download.
h2oBranch=master

echo "Fetching latest build number for branch ${h2oBranch}..."
curl --silent -o latest https://h2o-release.s3.amazonaws.com/h2o/${h2oBranch}/latest
h2oBuild=`cat latest`

echo "Fetching full version number for build ${h2oBuild}..."
curl --silent -o project_version https://h2o-release.s3.amazonaws.com/h2o/${h2oBranch}/${h2oBuild}/project_version
h2oVersion=`cat project_version`

echo "Downloading H2O version ${h2oVersion} to cluster..."

i=0
for publicDnsName in $(cat nodes-public)
do
    i=$((i+1))
    echo "Downloading h2o.jar to node ${i}: ${publicDnsName}"
    ssh -o StrictHostKeyChecking=no -i ${AWS_SSH_PRIVATE_KEY_FILE} ec2-user@${publicDnsName} curl --silent -o h2o-${h2oVersion}.zip https://s3.amazonaws.com/h2o-release/h2o/${h2oBranch}/${h2oBuild}/h2o-${h2oVersion}.zip &
done
wait

i=0
for publicDnsName in $(cat nodes-public)
do
    i=$((i+1))
    echo "Unzipping h2o.jar within node ${i}: ${publicDnsName}"
    ssh -o StrictHostKeyChecking=no -i ${AWS_SSH_PRIVATE_KEY_FILE} ec2-user@${publicDnsName} unzip -o h2o-${h2oVersion}.zip 1> /dev/null &
done
wait

i=0
for publicDnsName in $(cat nodes-public)
do
    i=$((i+1))
    echo "Copying h2o.jar within node ${i}: ${publicDnsName}"
    ssh -o StrictHostKeyChecking=no -i ${AWS_SSH_PRIVATE_KEY_FILE} ec2-user@${publicDnsName} cp -f h2o-${h2oVersion}/h2o.jar . &
done
wait

echo Success.
