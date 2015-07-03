#!/bin/bash

set -e

d=`dirname $0`

# Use 90% of RAM for H2O.
memTotalKb=`cat /proc/meminfo | grep MemTotal | sed 's/MemTotal:[ \t]*//' | sed 's/ kB//'`
memTotalMb=$[ $memTotalKb / 1024 ]
tmp=$[ $memTotalMb * 90 ]
xmxMb=$[ $tmp / 100 ]

# First try running java.
export JAVA_HOME=${d}/jdk1.7.0_80
echo JAVA_HOME is ${JAVA_HOME}
${JAVA_HOME}/bin/java -version

# Check that we can at least run H2O with the given java.
${JAVA_HOME}/bin/java -jar h2o.jar -version

# HDFS credentials.
hdfs_config_option=""
hdfs_config_value=""
hdfs_option=""
hdfs_option_value=""
hdfs_version=""
if [ -f .ec2/core-site.xml ]
then
    hdfs_config_option="-hdfs_config"
    hdfs_config_value=".ec2/core-site.xml"
    hdfs_option="-hdfs"
    hdfs_option_value=""
    hdfs_version=""
fi

# AWS credentials.
aws_credentials=""
if [ -f .ec2/aws_credentials.properties ]
then
    aws_credentials="--aws_credentials=.ec2/aws_credentials.properties"
fi

# Start H2O disowned in the background.
nohup ${JAVA_HOME}/bin/java -Xmx${xmxMb}m -jar h2o.jar -name H2ODemo -flatfile flatfile.txt -port 54321 -beta -ice_root ${d}/ice_root ${hdfs_config_option} ${hdfs_config_value} ${hdfs_option} ${hdfs_option_value} ${hdfs_version} ${aws_credentials} 1> h2o.out 2> h2o.err &

