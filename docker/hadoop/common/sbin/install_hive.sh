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

# Start HDFS, YARN, HIVE, etc.
# We need to start datanode as user hdfs. Otherwise, when invoked by root, it requires --privileged, which is not
# available while building the image. The datanode needs to be started by root in case of kerberos image, but shouldn't be needed here.
export STARTUP_DATANODE_USER_OVERRIDE='-u hdfs'
/usr/sbin/startup.sh

# Download dataset and upload it to HDFS
wget https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip
unzip AirlinesTest.csv.zip
rm AirlinesTest.csv.zip
sed -i 's/\"//g' AirlinesTest.csv

sudo -E -u hive hadoop fs -put -f ./AirlinesTest.csv /tmp/AirlinesTest.csv

# Execute all hive-scripts
cd /opt/hive-scripts
for f in $(ls); do
    sudo -E -u hive ${HIVE_HOME}/bin/beeline -u jdbc:hive2://localhost:10000 -f ${f}
done

cd /home/hive
sudo -E -u hive hadoop fs -put -f ./AirlinesTest.csv /tmp/AirlinesTest.csv
rm AirlinesTest.csv

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

