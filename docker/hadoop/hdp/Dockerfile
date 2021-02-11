ARG PARENT_VERSION
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-build-hadoop:${PARENT_VERSION}

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG VERSION
ARG PATH_PREFIX='.'
ARG PYTHON_VERSIONS='2.7,3.6'
ARG HIVE_PACKAGE='hive'

ENV DISTRIBUTION='hdp' \
    MASTER='yarn-client' \
    HADOOP_HOME=/usr/hdp/current/hadoop-client/ \
    HADOOP_CONF_DIR=/etc/hadoop/conf \
    MAPRED_USER=mapred \
    YARN_USER=yarn \
    YARN_CONF_DIR=/etc/hadoop/conf \
    HDFS_USER=hdfs \
    HIVE_PACKAGE=${HIVE_PACKAGE:-hive}

# Copy bin and sbin scripts
COPY ${PATH_PREFIX}/scripts/sbin ${PATH_PREFIX}/../common/sbin scripts/install_python_version /usr/sbin/

# Add HDP repository and install packages
RUN \
    apt-get update && \
    apt-get install -y wget curl software-properties-common git && \
    chmod 700 /usr/sbin/add_hdp_repo.sh && \
    sync && \
    /usr/sbin/add_hdp_repo.sh $VERSION && \
    rm /usr/sbin/add_hdp_repo.sh && \
    add-apt-repository -y ppa:deadsnakes && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        hadoop-conf-pseudo python-pip python-dev python-virtualenv \
        postgresql libpostgresql-jdbc-java \
        sudo unzip html2text slapd ldap-utils libkrb5-dev \
        vim

# Create hive user
RUN adduser --disabled-password --gecos "" hive

ARG H2O_BRANCH=master
ENV H2O_BRANCH=${H2O_BRANCH}

# Set required env vars and install Pythons
RUN \
  chmod 700 /usr/sbin/install_python_version && \
  sync && \
  /usr/sbin/install_python_version && \
  /usr/bin/activate_java_8

# Chown folders
RUN HDP_VERSION=$(ls /usr/hdp/ | grep -e '^2\|^3') && \
    chown hdfs:hdfs /usr/hdp/${HDP_VERSION}/hadoop && \
    chown yarn:yarn /usr/hdp/${HDP_VERSION}/hadoop-yarn && \
    chown yarn:yarn /usr/hdp/${HDP_VERSION}/hadoop-mapreduce && \
    chown -R root:hadoop /usr/hdp/current/hadoop-yarn*/bin/container-executor && \
    chmod -R 6050 /usr/hdp/current/hadoop-yarn*/bin/container-executor && \
    mkdir -p /usr/hdp/${HDP_VERSION}/hadoop/logs && \
    chown hdfs:hdfs /usr/hdp/${HDP_VERSION}/hadoop/logs && \
    chmod a+w /usr/hdp/${HDP_VERSION}/hadoop/logs

# Copy conf.pseudo to hadoop conf folder
RUN rm /usr/hdp/*/hadoop/conf/* && \
    cp /usr/hdp/*/etc/hadoop/conf.pseudo/* /usr/hdp/*/hadoop/conf/

# Copy hadoop configs
COPY ${PATH_PREFIX}/../common/conf/ ${PATH_PREFIX}/conf/ ${HADOOP_CONF_DIR}/

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

# Copy hive configs
COPY ${PATH_PREFIX}/../common/conf-hive/ /etc/${HIVE_PACKAGE}/conf/
COPY ${PATH_PREFIX}/conf-tez/ /etc/tez/conf/

# Copy hive data
RUN cd /home/jenkins/ && \
    wget https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip && \
    wget https://s3.amazonaws.com/h2o-public-test-data/smalldata/chicago/chicagoCensus.csv && \
    chown jenkins:jenkins /home/jenkins/*

RUN /usr/sbin/install_ldap.sh

# Expose ports
# H2O, Hadoop UI, Hive, LDAP
EXPOSE 54321 8088 10000 389

# Remove hadoop pids and fix permissions
RUN rm -f tmp/*.pid /var/run/hadoop-hdfs/*.pid && \
    chmod g-w /var/run/hadoop-* && \
    chown hdfs:hadoop /var/run/hadoop-hdfs && \
    chown mapred:hadoop /var/run/hadoop-mapreduce
