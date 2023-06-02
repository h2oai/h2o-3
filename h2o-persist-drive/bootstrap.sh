#!/usr/bin/env bash

set -ex

wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
tar xfz graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
GRAALVM_HOME="$PWD/$(ls -d graalvm-* | grep -F -v .tar.gz | tail -1)"
$GRAALVM_HOME/bin/gu install python
$GRAALVM_HOME/bin/graalpython -m venv venv
(source venv/bin/activate && pip install boto3)
ln -s $GRAALVM_HOME graalvm
