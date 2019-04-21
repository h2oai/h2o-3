ARG GPU_SUFFIX=''
ARG FROM_VERSION
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1${GPU_SUFFIX}:${FROM_VERSION}

RUN \
    curl -sL https://deb.nodesource.com/setup_10.x | bash - && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        texlive \
        texlive-fonts-extra \
        texlive-htmlxml \
        texinfo \
        texlive-bibtex-extra \
        texlive-formats-extra \
        texlive-generic-extra \
        python-virtualenv \
        nodejs \
        libkrb5-dev && \
    apt-get clean && \
    apt-get autoremove -y && \
    rm -rf /var/cache/apt/*

COPY scripts/build-h2o-3 scripts/install_python_version /tmp/
ENV PY_VERSION=3.5
ARG H2O_BRANCH=master
RUN \
    chown jenkins:jenkins /tmp/build-h2o-3 && \
    chmod a+x /tmp/build-h2o-3 /tmp/install_python_version && \
    sync && \
    /tmp/install_python_version ${PY_VERSION} && \
    rm /tmp/install_python_version

# Set GRADLE USER env var
ENV GRADLE_OPTS='-Dorg.gradle.daemon=false'
