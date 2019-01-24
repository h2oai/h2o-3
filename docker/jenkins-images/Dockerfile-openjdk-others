ARG GPU_SUFFIX=''
ARG FROM_VERSION
ARG FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-others-base
FROM ${FROM_IMAGE}${GPU_SUFFIX}:${FROM_VERSION}

ARG INSTALL_JAVA_VERSION
ENV JAVA_VERSION=${INSTALL_JAVA_VERSION}
COPY scripts/install_java_version_open_jdk /tmp/
RUN \
    chmod +x /tmp/install_java_version_open_jdk && \
    sync && \
    if [ "${INSTALL_JAVA_VERSION}" = "1.7.0" ]; then \
        /tmp/install_java_version_open_jdk ${INSTALL_JAVA_VERSION} 7; \
    else \
        echo "Unknown Java version ${INSTALL_JAVA_VERSION}"; \
        exit 1; \
    fi && \
    rm /tmp/install_java_version_open_jdk && \
    chmod a+w /usr/lib/jvm/

ENV \
  JAVA_HOME=/usr/lib/jvm/java-${INSTALL_JAVA_VERSION}-openjdk-amd64/ \
  PATH=/usr/lib/jvm/java-${INSTALL_JAVA_VERSION}-openjdk-amd64/bin:${PATH}
