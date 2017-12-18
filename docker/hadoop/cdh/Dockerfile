# pull base image
FROM ubuntu:16.04

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG VERSION
ARG PATH_PREFIX='.'
ARG DEFAULT_USER_UID=2117
ARG PYTHON_VERSIONS='2.7'

ENV DISTRIBUTION='cdh' VERSION=$VERSION HADOOP_CONF_DIR='/etc/hadoop/conf.pseudo' MASTER='yarn-client'≈

# Prepare Cloudera repository
RUN echo "# Packages for Cloudera's Distribution for Hadoop, Version 5, on Ubuntu 14.04 amd64\n\
deb [arch=amd64] http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n\
deb-src http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n" > /etc/apt/sources.list.d/cloudera.list
COPY ${PATH_PREFIX}/conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y curl wget && \
    wget http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key -O archive.key && \
    apt-key add archive.key && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y hadoop-conf-pseudo python-pip python-dev python-virtualenv libmysqlclient-dev sudo unzip && \
    rm -rf /var/lib/apt/lists/*

# Set required env vars and install Java 8 and Pythons
COPY ${PATH_PREFIX}/../common/sbin/ scripts/install_java scripts/install_python_versions /usr/sbin/
RUN \
  chmod 700 /usr/sbin/install_java && \
  chmod 700 /usr/sbin/install_python_versions && \
  sync && \
  /usr/sbin/install_java && \
  /usr/sbin/install_python_versions
ENV \
  JAVA_HOME=/usr/lib/jvm/java-8-oracle \
  PATH=/usr/lib/jvm/java-8-oracle/bin:${PATH}

# Initialize namenode
RUN service hadoop-hdfs-namenode init

# Add jenkins user
RUN adduser --disabled-password --gecos "" -u ${DEFAULT_USER_UID} jenkins

# Copy scripts
COPY ${PATH_PREFIX}/../common/startup ${PATH_PREFIX}/scripts/startup /etc/startup/

# Copy sudoers so we can start hadoop stuff without root access to container
COPY ${PATH_PREFIX}/../common/sudoers/jenkins /etc/sudoers.d/jenkins

# Expose ports
# H2O, HADOOP UI
EXPOSE 54321 8088

# Run this script on container run
RUN chmod 700 /usr/sbin/startup.sh
