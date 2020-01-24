ARG FROM_IMAGE
FROM ${FROM_IMAGE}

ARG PATH_PREFIX
ARG KRB

RUN rm -r /etc/hadoop/conf/* && \
    chown jenkins:jenkins /etc/hadoop/conf

# placeholder link to where krb confing will be downloaded
RUN if [ "${KRB}" = "true" ]; then ln -vsf /etc/hadoop/conf/krb5.conf /etc/krb5.conf; fi

# 0xg-6 cluster configs have this java location hard-coded
RUN mkdir -p /usr/jdk64 && \
    ln -vsf /usr/lib/jvm/java-8-oracle /usr/jdk64/jdk1.8.0_60

ENV HADOOP_CONF_DIR=/etc/hadoop/conf
