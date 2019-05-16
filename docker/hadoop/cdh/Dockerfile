# pull base image
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1-jdk-8:10

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG VERSION
RUN \
    if [ -z $VERSION ]; then \
        echo "build-arg VERSION must be set"; \
        exit 1; \
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

RUN \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y apt-transport-https curl wget software-properties-common git

# Prepare Cloudera repository
RUN \
    case ${VERSION} in \
    5*) \
        echo "# Packages for Cloudera's Distribution of Hadoop, Version 5\n\
deb [arch=amd64] http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n\
deb-src http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n" > /etc/apt/sources.list.d/cloudera.list && \
        wget http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key -O archive.key \
        ;; \
    6.*) \
        echo "# Packages for Cloudera's Distribution of Hadoop, Version ${VERSION}.0\n\
deb [arch=amd64] http://archive.cloudera.com/cdh6/${VERSION}.0/ubuntu1604/apt xenial-cdh${VERSION}.0 contrib\n" > /etc/apt/sources.list.d/cloudera.list && \
        wget https://archive.cloudera.com/cdh6/${VERSION}.0/ubuntu1604/apt/archive.key -O archive.key \
        ;; \
    *) \
        echo "Version ${VERSION} not supported" \
        ;; \
    esac
COPY ${PATH_PREFIX}/conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref

RUN apt-key add archive.key && \
    add-apt-repository -y ppa:deadsnakes && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        mysql-server libmysql-java libmysqlclient-dev \
        hadoop-conf-pseudo python-pip python-dev python-virtualenv \
        sudo unzip html2text slapd ldap-utils libkrb5-dev \
        vim

# Add Hive user
RUN adduser --disabled-password --gecos "" hive

ARG H2O_BRANCH=master
ENV H2O_BRANCH=${H2O_BRANCH}

# Set required env vars and install Pythons
COPY ${PATH_PREFIX}/scripts/sbin ${PATH_PREFIX}/../common/sbin scripts/install_python_version /usr/sbin/
RUN \
  chmod 700 /usr/sbin/install_python_version && \
  sync && \
  /usr/sbin/install_python_version && \
  /usr/bin/activate_java_8

# Copy hadoop configs
COPY ${PATH_PREFIX}/conf/ ${HADOOP_CONF_DIR}/

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
    chmod +x /usr/sbin/mysql_configure.sh && \
    chmod 700 /usr/sbin/mysql_configure.sh && \
    sync && \
    /usr/sbin/install_hive.sh && \
    if [ "${VERSION}" = "6.2" ] ; then echo "Decrease mysql connection timeout." && /usr/sbin/mysql_configure.sh; fi && \
    ln -sf /usr/share/java/mysql-connector-java.jar /usr/lib/hive/lib/mysql-connector-java.jar

# Copy hive configs
COPY ${PATH_PREFIX}/../common/conf-hive/ /etc/${HIVE_PACKAGE}/conf/

RUN /usr/sbin/install_ldap.sh

# Expose ports
# H2O, Hadoop UI, Hive, LDAP
EXPOSE 54321 8088 10000 389

# Remove hadoop pids
RUN rm -f tmp/*.pid /var/run/hadoop-*/*.pid
