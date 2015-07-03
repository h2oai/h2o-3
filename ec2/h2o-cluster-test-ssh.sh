#!/bin/bash

if [ -z ${AWS_SSH_PRIVATE_KEY_FILE} ]
then
    echo "ERROR: You must set AWS_SSH_PRIVATE_KEY_FILE in the environment."
    exit 1
fi

i=0
maxRetries=12
retries=0
for publicDnsName in $(cat nodes-public)
do
    if [ ${retries} -ge ${maxRetries} ]
    then
        echo "ERROR: Too many ssh retries."
        exit 1
    fi

    i=$((i+1))
    echo "Testing ssh to node ${i}: ${publicDnsName}"

    while true
    do
        ssh -o StrictHostKeyChecking=no -i ${AWS_SSH_PRIVATE_KEY_FILE} ec2-user@${publicDnsName} hostname
        if [ $? -eq 0 ]
        then
            break
        else
            retries=$((retries+1))

            if [ ${retries} -ge ${maxRetries} ]
            then
                echo "ERROR: Too many ssh retries."
                exit 1
            fi

            echo "Sleeping 5 seconds before retrying..."
            sleep 5
        fi
    done
done

echo Success.
