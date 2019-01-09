#! /bin/bash

set -x -e

if [ -z ${HIVE_VERSION} ]; then
    echo "HIVE_VERSION must be set"
    exit 1
fi

# Download and initialize HIVE
cd /home/hive
wget http://archive.apache.org/dist/hive/hive-${HIVE_VERSION}/apache-hive-${HIVE_VERSION}-bin.tar.gz
tar -xzf apache-hive-${HIVE_VERSION}-bin.tar.gz
rm apache-hive-${HIVE_VERSION}-bin.tar.gz
chown -R hive:hive apache-hive-*/
sudo -E -u hive ${HIVE_HOME}/bin/schematool -dbType derby -initSchema

mkdir -p /opt/hive-jars/
cp ${HIVE_HOME}/lib/hive-jdbc-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/hive-service-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/hive-metastore-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/hive-common-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/hive-cli-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/hive-exec-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/libfb303-* /opt/hive-jars/
cp ${HIVE_HOME}/lib/libthrift-* /opt/hive-jars/

find /opt/hive-jars/ -name '*.jar' | tr '\n' ',' > /opt/hive-jars/hive-libjars

