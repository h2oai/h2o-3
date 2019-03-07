#!/bin/bash -xe

apt-get install -y ${HIVE_PACKAGE} ${HIVE_PACKAGE}-server2

STANDALONE_JAR=$(find $HIVE_HOME/lib | grep 'jdbc-standalone' | tr '\n' ':' )
if [ "" = "$STANDALONE_JAR" ]
then
    find $HIVE_HOME/lib | grep '.jar' | \
        grep -E 'hive-jdbc|hive-common|hive-exec|hive-service|hive-metastore|libfb303|libthrift' | \
        tr '\n' ':' > /opt/hive-jdbc-cp
else
    echo ${STANDALONE_JAR} > /opt/hive-jdbc-cp
fi
