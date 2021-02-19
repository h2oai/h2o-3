ARG PARENT_VERSION
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-build-hadoop:${PARENT_VERSION}

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG VERSION
ARG REPO_VERSION
ARG APT_USERNAME
ARG APT_PASSWORD
RUN if [ -z $VERSION ]; then \
        echo "build-arg VERSION must be set"; \
        exit 1; \
    fi; \
    if [ -z $REPO_VERSION ]; then \
        REPO_VERSION=$VERSION; \
    fi
ARG PATH_PREFIX='.'
ARG PYTHON_VERSIONS='2.7,3.6'
ARG AWS_ACCESS_KEY
ARG AWS_SECRET_ACCESS_KEY
ARG HIVE_PACKAGE='hive'

ENV DISTRIBUTION='cdh' \
    HADOOP_HOME=/usr/lib/hadoop \
    HADOOP_CONF_DIR='/etc/hadoop/conf.pseudo' \
    MASTER='yarn-client' \
    HIVE_PACKAGE=${HIVE_PACKAGE:-hive} \
    HIVE_HOME=/usr/lib/${HIVE_PACKAGE}

COPY ${PATH_PREFIX}/scripts/sbin ${PATH_PREFIX}/../common/sbin scripts/install_python_version /usr/sbin/
COPY ${PATH_PREFIX}/conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref
RUN \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y apt-transport-https curl wget software-properties-common git && \
    chmod 700 /usr/sbin/add_cdh_repo.sh && \
    sync && \
    /usr/sbin/add_cdh_repo.sh $VERSION $REPO_VERSION $APT_USERNAME $APT_PASSWORD && \
    rm /usr/sbin/add_cdh_repo.sh && \
    echo "machine archive.cloudera.com login ${APT_USERNAME} password ${APT_PASSWORD}" > \
        /etc/apt/auth.conf.d/cloudera.conf && \
    apt-key add archive.key && \
    add-apt-repository -y ppa:deadsnakes && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        postgresql libpostgresql-jdbc-java \
        hadoop-conf-pseudo python-pip python-dev python-virtualenv \
        sudo unzip html2text slapd ldap-utils libkrb5-dev vim && \
    apt-get clean && \
    rm -rf /var/cache/apt/*

# Add Hive user
RUN adduser --disabled-password --gecos "" hive

ARG H2O_BRANCH=master
ENV H2O_BRANCH=${H2O_BRANCH}

# Set required env vars and install Pythons
RUN \
  chmod 700 /usr/sbin/install_python_version && \
  sync && \
  /usr/sbin/install_python_version && \
  /usr/bin/activate_java_8

# Copy hadoop configs
COPY ${PATH_PREFIX}/../common/conf/ ${PATH_PREFIX}/conf/ ${HADOOP_CONF_DIR}/

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
    chmod +x /usr/sbin/install_ldap.sh && \
    sync && \
    /usr/sbin/install_hive.sh && \
    ln -sf /usr/share/java/postgresql-jdbc4.jar /usr/lib/hive/lib/postgresql-jdbc4.jar

# Copy hive configs
COPY ${PATH_PREFIX}/../common/conf-hive/ /etc/${HIVE_PACKAGE}/conf/

# Copy hive data
RUN cd /home/jenkins/ && \
    wget https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip && \
    wget https://s3.amazonaws.com/h2o-public-test-data/smalldata/chicago/chicagoCensus.csv && \
    chown jenkins:jenkins /home/jenkins/*

RUN /usr/sbin/install_ldap.sh

# Expose ports
# H2O, Hadoop UI, Hive, LDAP
EXPOSE 54321 8088 10000 389

# Remove hadoop pids
RUN rm -f tmp/*.pid /var/run/hadoop-*/*.pid
