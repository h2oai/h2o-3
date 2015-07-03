#!/bin/bash

set -e

if [ -z ${AWS_ACCESS_KEY_ID} ]
then
    echo "ERROR: You must set AWS_ACCESS_KEY_ID in the environment."
    exit 1
fi

if [ -z ${AWS_SECRET_ACCESS_KEY} ]
then
    echo "ERROR: You must set AWS_SECRET_ACCESS_KEY in the environment."
    exit 1
fi

if [ -z ${AWS_SSH_PRIVATE_KEY_FILE} ]
then
    echo "ERROR: You must set AWS_SSH_PRIVATE_KEY_FILE in the environment."
    exit 1
fi

coreSiteFileName=core-site.xml
rm -f ${coreSiteFileName}
cat <<EOF1 > ${coreSiteFileName}
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>

<!-- Put site-specific property overrides in this file. -->

<configuration>

<!--
<property>
  <name>fs.default.name</name>
  <value>s3n://h2o-datasets</value>
</property>
-->

<property>
  <name>fs.s3n.awsAccessKeyId</name>
  <value>${AWS_ACCESS_KEY_ID}</value>
</property>

<property>
  <name>fs.s3n.awsSecretAccessKey</name>
  <value>${AWS_SECRET_ACCESS_KEY}</value>
</property>

</configuration>
EOF1

awsCredentialsPropertiesFileName=aws_credentials.properties
rm -f ${awsCredentialsPropertiesFileName}
cat <<EOF2 > ${awsCredentialsPropertiesFileName}
accessKey=${AWS_ACCESS_KEY_ID}
secretKey=${AWS_SECRET_ACCESS_KEY}
EOF2

i=0
for publicDnsName in $(cat nodes-public)
do
    i=$((i+1))
    echo "Copying aws credential files to node ${i}: ${publicDnsName}"
    scp -p -o StrictHostKeyChecking=no -i ${AWS_SSH_PRIVATE_KEY_FILE} ${coreSiteFileName} ${awsCredentialsPropertiesFileName} ec2-user@${publicDnsName}:.ec2
done

echo Success.
