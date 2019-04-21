#!/bin/bash

#Create Flatfile
touch /opt/h2oai/flatfile.txt
flatfile="/opt/h2oai/flatfile.txt"

wget http://s3.amazonaws.com/ec2metadata/ec2-metadata
sudo chmod u+x ec2-metadata
INSTANCE_ID=$(./ec2-metadata | grep instance-id | awk 'NR==1{print $2}')

AG_NAME=$(aws autoscaling describe-auto-scaling-instances --instance-ids ${INSTANCE_ID} --region eu-west-1 --query AutoScalingInstances[].AutoScalingGroupName --output text)
for ID in $(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names ${AG_NAME} --region eu-west-1 --query AutoScalingGroups[].Instances[].InstanceId --output text);
do    
    IP=$(aws ec2 describe-instances --instance-ids $ID --region eu-west-1 --query Reservations[].Instances[].PrivateIpAddress --output text)
    echo "${IP}:54321 " >> "$flatfile"
done

