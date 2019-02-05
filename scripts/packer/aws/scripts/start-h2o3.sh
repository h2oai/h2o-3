#!/bin/bash -e

# Use 90% of RAM for H2O.
memTotalKb=`cat /proc/meminfo | grep MemTotal | sed 's/MemTotal:[ \t]*//' | sed 's/ kB//'`
memTotalMb=$[ $memTotalKb / 1024 ]
tmp=$[ $memTotalMb * 90 ]
xmxMb=$[ $tmp / 100 ]

cd /home/ubuntu
mkdir -p log config

# AWS credentials.
aws_credentials=""
if [ -f .ec2/aws_credentials.properties ]
then
    aws_credentials="--aws_credentials=.ec2/aws_credentials.properties"
fi

if [ -f /home/ubuntu/config/flatfile ]
then
    flatfile="-flatfile /home/ubuntu/config/flatfile"
fi

nohup java -Xmx${xmxMb}m -jar /home/ubuntu/h2o-3/h2o.jar -name Puddle ${flatfile} -port 54321 ${aws_credentials} 1> /home/ubuntu/log/h2o.out 2> /home/ubuntu/log/h2o.err &
