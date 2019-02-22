#!/bin/bash -xe

apt-get install -y ${HIVE_PACKAGE} ${HIVE_PACKAGE}-server2

source /usr/sbin/get_hive_home.sh

find $HIVE_HOME/lib | grep '.jar' | \
    grep -E 'hive-jdbc|hive-common|hive-exec|hive-service|hive-metastore|libfb303|libthrift' | \
    tr '\n' ':' > /opt/hive-jdbc-cp

ln -sf /usr/share/java/mysql-connector-java.jar ${HIVE_HOME}/lib/mysql-connector-java.jar
