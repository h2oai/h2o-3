#!/bin/bash -ex

if [ -z "${H2O3_DOWNLOAD_URL}" ]; then
    echo "Pass download URL as first argument"
    exit 1
fi

sudo apt update
sudo apt -y --no-install-recommends install \
    wget \
    unzip \
    default-jre

cd /home/ubuntu

wget --quiet ${H2O3_DOWNLOAD_URL}
unzip h2o-*.zip
rm h2o-*.zip
ln -s h2o-${H2O3_VERSION} h2o-3

sudo mv rc.local /etc/
sudo chown root:root /etc/rc.local
sudo chmod a+x /etc/rc.local

chmod a+x scripts/start-h2o3.sh scripts/stop-h2o3.sh

mkdir log data

java -jar h2o-3/h2o.jar --version
