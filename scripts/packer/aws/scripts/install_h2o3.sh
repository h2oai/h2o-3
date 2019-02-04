#!/bin/bash

set -ex

if [ -z "${H2O3_DOWNLOAD_URL}" ]; then
    echo "Pass download URL as first argument"
    exit 1
fi

sudo apt update
sudo apt -y --no-install-recommends install \
    wget \
    unzip \
    default-jre

wget ${H2O3_DOWNLOAD_URL}
unzip h2o-*.zip
rm h2o-*.zip

chmod a+x scripts/*.sh

mkdir log data

java -jar h2o-3*/h2o.jar --version
