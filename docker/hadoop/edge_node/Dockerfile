ARG FROM_IMAGE
FROM ${FROM_IMAGE}

ARG PATH_PREFIX
ARG KRB

RUN rm -r /etc/hadoop/conf/* && \
    chown jenkins:jenkins /etc/hadoop/conf

RUN if [ "${KRB}" = "true" ]; then ln -vsf /etc/hadoop/conf/krb5.conf /etc/krb5.conf; fi

ENV HADOOP_CONF_DIR=/etc/hadoop/conf
