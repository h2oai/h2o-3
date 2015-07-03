#!/bin/bash

set -e

if [ -z ${AWS_SSH_PRIVATE_KEY_FILE} ]
then
    echo "ERROR: You must set AWS_SSH_PRIVATE_KEY_FILE in the environment."
    exit 1
fi

d=`dirname $0`
for possibleH2oJarFile in ${d}/h2o.jar ${d}/../h2o.jar ${d}/../target/h2o.jar
do
    if [ -f ${possibleH2oJarFile} ]
    then
        h2oJarFile=${possibleH2oJarFile}
        break
    fi
done

if [ -z ${h2oJarFile} ]
then
    echo "ERROR: Cannot file h2o.jar file."
    exit 1
fi

echo Using ${h2oJarFile}

i=0
for publicDnsName in $(cat nodes-public)
do
    i=$((i+1))
    echo "Copying h2o.jar to node ${i}: ${publicDnsName}"
    scp -o StrictHostKeyChecking=no -i ${AWS_SSH_PRIVATE_KEY_FILE} ${h2oJarFile} ec2-user@${publicDnsName}:
done

echo Success.
