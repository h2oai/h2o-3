# pull base image
FROM ubuntu:16.04

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG VERSION
ARG PATH_PREFIX='.'
ARG DEFAULT_USER_UID=2117
ARG PYTHON_VERSIONS='2.7,3.6'
ARG JAVA_VERSIONS='8'
ARG HIVE_VERSION='2.2.0'

ENV DISTRIBUTION='cdh' \
    HADOOP_HOME=/usr/lib/hadoop \
    HADOOP_CONF_DIR='/etc/hadoop/conf.pseudo' \
    MASTER='yarn-client' \
    HIVE_HOME=/home/hive/apache-hive-${HIVE_VERSION}-bin

# Prepare Cloudera repository
RUN echo "# Packages for Cloudera's Distribution for Hadoop, Version 5, on Ubuntu 14.04 amd64\n\
deb [arch=amd64] http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n\
deb-src http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n" > /etc/apt/sources.list.d/cloudera.list
COPY ${PATH_PREFIX}/conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y curl wget software-properties-common git && \
    wget http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key -O archive.key && \
    apt-key add archive.key && \
    add-apt-repository -y ppa:deadsnakes && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y hadoop-conf-pseudo python-pip python-dev python-virtualenv libmysqlclient-dev sudo unzip html2text slapd ldap-utils

# Add jenkins and users
RUN adduser --disabled-password --gecos "" -u ${DEFAULT_USER_UID} jenkins && \
    adduser --disabled-password --gecos "" hive

ENV H2O_BRANCH='master'

# Set required env vars and install Java 8 and Pythons
COPY ${PATH_PREFIX}/../common/sbin/ scripts/install_java_version scripts/java-*-vars.sh scripts/install_java_versions scripts/install_python_version /usr/sbin/
RUN \
  chmod 700 /usr/sbin/install_java_version && \
  chmod 700 /usr/sbin/install_java_versions && \
  chmod 700 /usr/sbin/java-*-vars.sh && \
  chmod 700 /usr/sbin/install_python_version && \
  sync && \
  /usr/sbin/install_java_versions && \
  /usr/sbin/install_python_version && \
  /usr/bin/activate_java_8
ENV \
  JAVA_HOME=/usr/lib/jvm/java-current-oracle \
  PATH=/usr/lib/jvm/java-current-oracle/bin:${PATH}

# Copy configs
COPY ${PATH_PREFIX}/conf/ ${HADOOP_CONF_DIR}

# Initialize namenode
RUN service hadoop-hdfs-namenode init

# Copy scripts
COPY ${PATH_PREFIX}/../common/startup ${PATH_PREFIX}/scripts/startup /etc/startup/

# Copy sudoers so we can start hadoop stuff without root access to container
COPY ${PATH_PREFIX}/../common/sudoers/jenkins /etc/sudoers.d/jenkins
COPY ${PATH_PREFIX}/../common/hive-scripts /opt/hive-scripts/
COPY ${PATH_PREFIX}/../common/ldap /opt/ldap-scripts/

# Run this script on container run
RUN chmod 700 /usr/sbin/startup.sh && \
    chown -R hive:hive /opt/hive-scripts && \
    chmod +x /usr/sbin/install_hive.sh && \
    chmod 700 /usr/sbin/install_ldap.sh && \
    sync && \
    /usr/sbin/install_hive.sh

RUN /usr/sbin/install_ldap.sh

# Expose ports
# H2O, Hadoop UI, Hive, LDAP
EXPOSE 54321 8088 10000 389

# Remove hadoop pids
RUN rm -f tmp/*.pid /var/run/hadoop-*/*.pid
