#!/usr/bin/env bash

curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name -o /tmp/instance.name
curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone -o /tmp/instance.zone
curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/id -o /tmp/instance.id
curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/servers -o /tmp/instance.servers

# install dependencies
yum install -y wget unzip python3

# Install terraform
mkdir -p /tmp/terraform
pushd /tmp/terraform
curl $(curl --silent  https://www.terraform.io/downloads.html | grep '_linux_amd64.zip' | cut -d '"' -f 2) -o terraform.zip
unzip terraform.zip
mv terraform /usr/bin
popd

## Install google cloud sdk
#tee -a /etc/yum.repos.d/google-cloud-sdk.repo << EOM
#[google-cloud-sdk]
#name=Google Cloud SDK
#baseurl=https://packages.cloud.google.com/yum/repos/cloud-sdk-el7-x86_64
#enabled=1
#gpgcheck=1
#repo_gpgcheck=1
#gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg
#       https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
#EOM
#yum install google-cloud-sdk

# Get h2ocluster terraform code and move it to 
mkdir -p /tmp/temp
pushd /tmp/temp
curl https://0xdata-public.s3.amazonaws.com/hemen/h2ocluster.zip -o h2ocluster.zip
unzip h2ocluster.zip
mv h2ocluster /opt
chown -R root:root /opt/h2ocluster
chmod o+x /opt/h2ocluster/h2ocluster.sh
chmod o+r /opt/h2ocluster/gcpkey.json
popd

