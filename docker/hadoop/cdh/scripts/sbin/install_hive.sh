#!/bin/bash -xe

apt-get install -y ${HIVE_PACKAGE} ${HIVE_PACKAGE}-server2

find /usr/lib/hive/lib | grep 'jdbc-standalone' | tr '\n' ':' > /opt/hive-jdbc-cp
