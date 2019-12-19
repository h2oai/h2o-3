ARG FROM_IMAGE
FROM ${FROM_IMAGE}

ARG PATH_PREFIX

RUN rm -r /etc/hadoop/conf/* && \
    chown jenkins:jenkins /etc/hadoop/conf

ENV HADOOP_CONF_DIR=/etc/hadoop/conf
