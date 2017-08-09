FROM nimmis/java:14.04-oracle-8-jdk

ARG VERSION
ARG PATH_PREFIX='.'

ENV DISTRIBUTION 'cdh'
ENV VERSION $VERSION
ENV SETUP_H2O TRUE
ENV START_H2O TRUE
ENV RUN_TESTS TRUE
ENV ENTER_BASH FALSE

RUN echo "# Packages for Cloudera's Distribution for Hadoop, Version 5, on Ubuntu 14.04 amd64\n\
deb [arch=amd64] http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n\
deb-src http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n" > /etc/apt/sources.list.d/cloudera.list

# Prepare Cloudera repository
COPY ${PATH_PREFIX}/conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref
RUN wget http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key -O archive.key && sudo apt-key add archive.key && \
    sudo apt-get update

# Download necessary packages
RUN apt-get install -y hadoop-conf-pseudo python-dev python-pip python-scipy
RUN pip install requests --upgrade
RUN pip install colorama future tabulate pandas

# Initialize namenode
RUN service hadoop-hdfs-namenode init

# Add H2O user
RUN useradd -ms /bin/bash h2o

# Copy startup scripts
COPY "${PATH_PREFIX}/scripts/hdfs_start" "/etc/startup/00_hdfs_start"
RUN chmod 700 "/etc/startup/00_hdfs_start"
COPY "${PATH_PREFIX}/../common/hdfs_setup" "/etc/startup/10_hdfs_setup"
RUN chmod 700 "/etc/startup/10_hdfs_setup"
COPY "${PATH_PREFIX}/../common/hdfs_create_h2o_home" "/etc/startup/20_hdfs_create_h2o_home"
RUN chmod 700 "/etc/startup/20_hdfs_create_h2o_home"
COPY "${PATH_PREFIX}/scripts/yarn_start" "/etc/startup/30_yarn_start"
RUN chmod 700 "/etc/startup/30_yarn_start"
COPY "${PATH_PREFIX}/../common/setup_h2o" "/etc/startup/40_setup_h2o"
RUN chmod 700 "/etc/startup/40_setup_h2o"
COPY "${PATH_PREFIX}/../common/start_h2o" "/etc/startup/45_start_h2o"
RUN chmod 700 "/etc/startup/45_start_h2o"
COPY "${PATH_PREFIX}/../common/startup.sh" "/usr/bin/startup.sh"
RUN chmod +x /usr/bin/startup.sh

# Expose ports
# H2O
EXPOSE 54321
# HADOOP UI
EXPOSE 8088

# Run this script on container run
CMD "/usr/bin/startup.sh"
