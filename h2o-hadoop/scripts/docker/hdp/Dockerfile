FROM nimmis/java:14.04-oracle-8-jdk

WORKDIR /

ARG VERSION
ARG PATH_PREFIX='.'
ARG SPARK_VERSIONS=''

ENV DISTRIBUTION='hdp' VERSION=$VERSION ENTER_BASH=FALSE HADOOP_CONF_DIR='/usr/hdp/2*/hadoop/conf/' MASTER='yarn-client' YARN_DAEMON='/usr/hdp/2*/hadoop-yarn/sbin/yarn-daemon.sh' MAPRED_USER=mapred YARN_USER=yarn HDFS_USER=hdfs

# Copy bin, sbin and startup scripts
COPY ${PATH_PREFIX}/scripts/bin ${PATH_PREFIX}/../common/bin /usr/bin/
COPY ${PATH_PREFIX}/scripts/sbin ${PATH_PREFIX}/../common/sbin /usr/sbin/
COPY ${PATH_PREFIX}/scripts/startup ${PATH_PREFIX}/../common/startup /etc/startup/

# Add HDP repository
RUN chmod 700 /usr/sbin/add_hdp_repo.sh && \
    /usr/sbin/add_hdp_repo.sh $VERSION && \
    rm /usr/sbin/add_hdp_repo.sh && \
    apt-get update

# Install HDP and required packages
RUN apt-get install -y hadoop-conf-pseudo python-dev python-pip python-scipy && \
    pip install requests colorama future tabulate pandas --upgrade

# Copy configs
COPY ${PATH_PREFIX}/conf/ /etc/hadoop/conf/

# Chown folders
RUN chown hdfs:hdfs /usr/hdp/2*/hadoop && \
    chown yarn:yarn /usr/hdp/2*/hadoop-yarn && \
    chown yarn:yarn /usr/hdp/2*/hadoop-mapreduce && \
    chown -R root:hadoop /usr/hdp/current/hadoop-yarn*/bin/container-executor && \
    chmod -R 6050 /usr/hdp/current/hadoop-yarn*/bin/container-executor

# Copy conf.pseudo to hadoop conf folder
RUN rm /usr/hdp/2*/hadoop/conf/* && \
    cp /usr/hdp/2*/etc/hadoop/conf.pseudo/* /usr/hdp/2*/hadoop/conf/

# Generate mapred-site.xml
RUN chmod 700 /usr/sbin/generate-mapred-site && \
    /usr/sbin/generate-mapred-site && \
    rm /usr/sbin/generate-mapred-site

# Generate yarn-site.xml
RUN chmod 700 /usr/sbin/generate-yarn-site && \
    /usr/sbin/generate-yarn-site && \
    rm /usr/sbin/generate-yarn-site

# Format namenode
RUN su - hdfs -c "/usr/hdp/current/hadoop-hdfs-namenode/../hadoop/bin/hdfs namenode -format"

# Create h2o user
RUN useradd -ms /bin/bash h2o

# Download Spark if required
RUN chmod 700 /usr/sbin/download_spark && \
    /usr/sbin/download_spark

# Expose ports
# H2O, Hadoop UI
EXPOSE 54321 8088

RUN chmod 700 /usr/bin/startup.sh
CMD "/usr/bin/startup.sh"
