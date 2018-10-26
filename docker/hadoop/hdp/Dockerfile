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

ENV DISTRIBUTION='hdp' \
    MASTER='yarn-client' \
    HADOOP_HOME=/usr/hdp/current/hadoop-client/ \
    HADOOP_CONF_DIR=/etc/hadoop/conf \
    HADOOP_DAEMON=/usr/hdp/2*/hadoop/sbin/hadoop-daemon.sh \
    YARN_DAEMON=/usr/hdp/2*/hadoop-yarn/sbin/yarn-daemon.sh \
    MAPRED_USER=mapred \
    YARN_USER=yarn \
    HDFS_USER=hdfs \
    HIVE_HOME=/home/hive/apache-hive-${HIVE_VERSION}-bin

# Copy bin and sbin scripts
COPY ${PATH_PREFIX}/scripts/sbin ${PATH_PREFIX}/../common/sbin scripts/install_java_version scripts/install_java_versions scripts/java-*-vars.sh scripts/install_python_version /usr/sbin/

# Add HDP repository and install packages
RUN apt-get update && \
    apt-get install -y wget curl software-properties-common git && \
    chmod 700 /usr/sbin/add_hdp_repo.sh && \
    sync && \
    /usr/sbin/add_hdp_repo.sh $VERSION && \
    rm /usr/sbin/add_hdp_repo.sh && \
    add-apt-repository -y ppa:deadsnakes && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y hadoop-conf-pseudo python-dev python-pip python-dev python-virtualenv libmysqlclient-dev sudo unzip html2text slapd ldap-utils

# Create jenkins and hive users
RUN adduser --disabled-password --gecos "" -u ${DEFAULT_USER_UID} jenkins && \
    adduser --disabled-password --gecos "" hive

ENV H2O_BRANCH='master'

# Set required env vars and install Java 8 and Pythons
RUN \
  chmod 700 /usr/sbin/install_java_version && \
  chmod 700 /usr/sbin/install_java_versions && \
  chmod 700 /usr/sbin/java-*-vars.sh && \
  chmod 700 /usr/sbin/install_python_version && \
  sync && \
  /usr/sbin/install_python_version && \
  /usr/sbin/install_java_versions && \
  /usr/bin/activate_java_8
ENV \
  JAVA_HOME=/usr/lib/jvm/java-current-oracle \
  PATH=/usr/lib/jvm/java-current-oracle/bin:${PATH}

# Chown folders
RUN chown hdfs:hdfs /usr/hdp/2*/hadoop && \
    chown yarn:yarn /usr/hdp/2*/hadoop-yarn && \
    chown yarn:yarn /usr/hdp/2*/hadoop-mapreduce && \
    chown -R root:hadoop /usr/hdp/current/hadoop-yarn*/bin/container-executor && \
    chmod -R 6050 /usr/hdp/current/hadoop-yarn*/bin/container-executor

# Copy conf.pseudo to hadoop conf folder
RUN rm /usr/hdp/2*/hadoop/conf/* && \
    cp /usr/hdp/2*/etc/hadoop/conf.pseudo/* /usr/hdp/2*/hadoop/conf/

# Copy configs
COPY ${PATH_PREFIX}/conf/ ${HADOOP_CONF_DIR}

# Generate mapred-site.xml
RUN chmod 700 /usr/sbin/generate-mapred-site && \
    sync && \
    /usr/sbin/generate-mapred-site && \
    rm /usr/sbin/generate-mapred-site

# Generate yarn-site.xml
RUN chmod 700 /usr/sbin/generate-yarn-site && \
    sync && \
    /usr/sbin/generate-yarn-site && \
    rm /usr/sbin/generate-yarn-site

# Format namenode
RUN su - hdfs -c "/usr/hdp/current/hadoop-hdfs-namenode/../hadoop/bin/hdfs namenode -format"

# Copy startup scripts
COPY ${PATH_PREFIX}/scripts/startup ${PATH_PREFIX}/../common/startup /etc/startup/

# Copy sudoers so we can start hadoop stuff without root access to container
COPY ${PATH_PREFIX}/../common/sudoers/jenkins /etc/sudoers.d/jenkins
COPY ${PATH_PREFIX}/../common/hive-scripts /opt/hive-scripts/
COPY ${PATH_PREFIX}/../common/ldap /opt/ldap-scripts/

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
RUN rm -f tmp/*.pid /var/run/hadoop-hdfs/*.pid
