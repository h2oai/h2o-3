#!/bin/bash -xe

apt-get install -y ${HIVE_PACKAGE} ${HIVE_PACKAGE}-server2

source /usr/sbin/get_hive_home.sh

find $HIVE_HOME/lib | grep '.jar' | \
    grep -E 'hive-jdbc|hive-common|hive-exec|hive-service|hive-metastore|libfb303|libthrift' | \
    tr '\n' ':' > /opt/hive-jdbc-cp

# hack for missing xerces classes in Hive classpath in hdp 2.3, 2.4, 2.5
find $HADOOP_HOME -name "*xercesImpl.jar" | tr '\n' ':' >> /opt/hive-jdbc-cp

ln -sf /usr/share/java/postgresql-jdbc4.jar ${HIVE_HOME}/lib/postgresql-jdbc4.jar
