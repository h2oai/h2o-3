#!/usr/bin/env bash
set -o xtrace

ZONE=$(basename $(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone))
INSTANCE=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name)

# install dependencies
yum install -y wget unzip python3 tree

# Install terraform
mkdir -p /tmp/terraform
pushd /tmp/terraform
curl --silent https://releases.hashicorp.com/terraform/0.13.5/terraform_0.13.5_linux_amd64.zip -o terraform.zip
unzip terraform.zip
mv terraform /usr/bin
popd

# Install packer 
mkdir -p /tmp/packer
pushd /tmp/packer
curl --silent https://releases.hashicorp.com/packer/1.6.5/packer_1.6.5_linux_amd64.zip -o packer.zip
unzip packer.zip
mv packer /usr/bin
popd

# Get h2ocluster terraform code and move it to 
mkdir -p /tmp/temp
pushd /tmp/temp
curl --silent https://0xdata-public.s3.amazonaws.com/hemen/h2ocluster.zip -o h2ocluster.zip
unzip h2ocluster.zip
mv h2ocluster /opt
mv /opt/h2ocluster/terraform/h2ocluster.sh /opt/h2ocluster/terraform/h2ocluster
chown -R root:root /opt/h2ocluster
chmod o+x /opt/h2ocluster/terraform/h2ocluster
chmod o+r /opt/h2ocluster/terraform/gcpkey.json
popd

# install jq
wget -O /usr/bin/jq https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64
chmod +x /usr/bin/jq

# shellcheck disable=SC2016
echo 'PATH="/opt/h2ocluster/terraform:$PATH"' > /etc/profile.d/h2ocluster.sh

# Signal Startup script completion
# gcloud compute instances add-metadata ${INSTANCE} --metadata startup-complete=TRUE --zone=${ZONE}
