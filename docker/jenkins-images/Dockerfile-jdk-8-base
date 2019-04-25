ARG GPU_SUFFIX=''
ARG FROM_VERSION
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-base${GPU_SUFFIX}:${FROM_VERSION}

ENV JAVA_VERSION=8
COPY scripts/install_java_version_local scripts/java-${JAVA_VERSION}-vars.sh jdk1.8.0_171.zip /tmp/
RUN \
    chmod +x /tmp/install_java_version_local /tmp/java-${JAVA_VERSION}-vars.sh && \
    sync && \
    /tmp/install_java_version_local ${JAVA_VERSION} /tmp/java-${JAVA_VERSION}-vars.sh && \
    rm /tmp/install_java_version_local /tmp/java-${JAVA_VERSION}-vars.sh /tmp/jdk1.8.0_171.zip && \
    chmod a+w /usr/lib/jvm/

ENV \
  JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}-oracle \
  PATH=/usr/lib/jvm/java-${JAVA_VERSION}-oracle/bin:${PATH}
