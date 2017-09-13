FROM nimmis/java:14.04-oracle-8-jdk

ARG VERSION
ARG PATH_PREFIX='.'
ARG SPARK_VERSIONS=''
ARG DEFAULT_USER_UID=1000

ENV DISTRIBUTION='cdh' VERSION=$VERSION ENTER_BASH=FALSE HADOOP_CONF_DIR='/etc/hadoop/conf.pseudo' MASTER='yarn-client' PATH=/opt/spark-current/bin:$PATH SPARK_HOME=/opt/spark-current

# Prepare Cloudera repository
RUN echo "# Packages for Cloudera's Distribution for Hadoop, Version 5, on Ubuntu 14.04 amd64\n\
deb [arch=amd64] http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n\
deb-src http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n" > /etc/apt/sources.list.d/cloudera.list
COPY ${PATH_PREFIX}/conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref

RUN wget http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key -O archive.key && \
    sudo apt-key add archive.key && \
    sudo apt-get update

# Download necessary packages
RUN apt-get install -y hadoop-conf-pseudo python-dev python-pip python-scipy && \
    pip install requests colorama future tabulate pandas --upgrade

# Initialize namenode
RUN service hadoop-hdfs-namenode init

# Add H2O user
RUN adduser --disabled-password --gecos "" -u ${DEFAULT_USER_UID} h2o

# Copy scripts
COPY ${PATH_PREFIX}/../common/startup ${PATH_PREFIX}/scripts/startup /etc/startup/
COPY ${PATH_PREFIX}/../common/bin/ /usr/bin/
COPY ${PATH_PREFIX}/../common/sbin/ /usr/sbin/

# Download Spark, if required
RUN chmod 700 /usr/sbin/install_spark && \
    sync && \
    /usr/sbin/install_spark

# Expose ports
# H2O, HADOOP UI
EXPOSE 54321 8088

# Run this script on container run
RUN chmod 700 /usr/sbin/startup.sh
CMD "/usr/sbin/startup.sh"
