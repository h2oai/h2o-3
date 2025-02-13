ARG FROM_IMAGE
FROM ${FROM_IMAGE}

MAINTAINER h2oai "h2o.ai"

ENV LANG C.UTF-8

# Install required packages
RUN \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        build-essential \
        curl \
        chrpath \
        git \
        software-properties-common \
        unzip \
        wget \
        libffi-dev \
        libxml2-dev \
        libssl-dev \
        libcurl4-openssl-dev \
        libxft-dev \
        libmysqlclient-dev && \
   apt-get clean && \
   apt-get remove -y --purge man-db && \
   rm -rf /usr/share/man /usr/share/doc /var/cache/apt/*

# Create jenkins user
ARG JENKINS_UID=2117
ARG JENKINS_GID=2117
RUN \
    groupadd -g ${JENKINS_GID} jenkins && \
    adduser --uid ${JENKINS_UID} -gid ${JENKINS_GID} --disabled-password --gecos "" jenkins
