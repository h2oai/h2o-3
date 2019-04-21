ARG GPU_SUFFIX=''
ARG FROM_VERSION
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-base${GPU_SUFFIX}:${FROM_VERSION}

RUN \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        python-virtualenv libkrb5-dev && \
    apt-get clean && \
    rm -rf /var/cache/apt/*

COPY scripts/install_java_version scripts/install_python_version /tmp/
ENV PYTHON_VERSION=2.7
ARG H2O_BRANCH='master'
RUN \
    chmod +x /tmp/install_java_version /tmp/install_python_version && \
    sync && \
    /tmp/install_python_version ${PYTHON_VERSION} && \
    rm /tmp/install_python_version
