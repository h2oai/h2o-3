#!/bin/bash

# Get Instance ID
EC2_INSTANCE_ID=$(ec2metadata --instance-id)

# Set password for nginx to Instance ID
htpasswd -b -c /opt/h2oai/htpasswd h2oai $EC2_INSTANCE_ID
