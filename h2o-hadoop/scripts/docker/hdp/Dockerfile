FROM nimmis/java:14.04-oracle-8-jdk

WORKDIR /

ARG VERSION
ARG PATH_PREFIX='.'

ENV DISTRIBUTION 'hdp'
ENV VERSION $VERSION
ENV SETUP_H2O TRUE
ENV START_H2O TRUE
ENV RUN_TESTS TRUE
ENV ENTER_BASH FALSE

# Add HDP repository
COPY ${PATH_PREFIX}/scripts/add_hdp_repo.sh /usr/sbin/add_hdp_repo.sh
RUN chmod 700 /usr/sbin/add_hdp_repo.sh
RUN /usr/sbin/add_hdp_repo.sh $VERSION

# Install HDP and required packages
RUN apt-get install -y hadoop-conf-pseudo python-dev python-pip python-scipy
RUN pip install requests --upgrade
RUN pip install colorama future tabulate pandas

# Copy configs
COPY ${PATH_PREFIX}/conf/hadoop-env.sh /etc/hadoop/conf/hadoop-env.sh
COPY ${PATH_PREFIX}/conf/core-site.xml /etc/hadoop/conf/core-site.xml

# Chown folders
RUN chown hdfs:hdfs /usr/hdp/2*/hadoop
RUN chown yarn:yarn /usr/hdp/2*/hadoop-yarn
RUN chown yarn:yarn /usr/hdp/2*/hadoop-mapreduce
RUN chown -R root:hadoop /usr/hdp/current/hadoop-yarn*/bin/container-executor
RUN chmod -R 6050 /usr/hdp/current/hadoop-yarn*/bin/container-executor

# Export required ENV variables
ENV YARN_DAEMON /usr/hdp/2*/hadoop-yarn/sbin/yarn-daemon.sh
ENV HADOOP_CONF_DIR /usr/hdp/2*/hadoop/conf/
ENV MAPRED_USER mapred
ENV YARN_USER yarn
ENV HDFS_USER hdfs

# Copy conf.pseudo to hadoop conf folder
RUN rm /usr/hdp/2*/hadoop/conf/*
RUN cp /usr/hdp/2*/etc/hadoop/conf.pseudo/* /usr/hdp/2*/hadoop/conf/

# Generate mapred-site.xml
COPY ${PATH_PREFIX}/scripts/generate-mapred-site /usr/sbin/generate-mapred-site
RUN chmod +x /usr/sbin/generate-mapred-site
RUN /usr/sbin/generate-mapred-site
RUN rm /usr/sbin/generate-mapred-site

# Generate yarn-site.xml
COPY ${PATH_PREFIX}/scripts/generate-yarn-site /usr/sbin/generate-yarn-site
RUN chmod +x /usr/sbin/generate-yarn-site
RUN /usr/sbin/generate-yarn-site
RUN rm /usr/sbin/generate-yarn-site

# Format namenode
RUN su - hdfs -c "/usr/hdp/current/hadoop-hdfs-namenode/../hadoop/bin/hdfs namenode -format"

# Create h2o user
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

# Expose H2O and Hadoop UI ports
# H2O
EXPOSE 54321
# HADOOP UI
EXPOSE 8088

CMD "/usr/bin/startup.sh"
