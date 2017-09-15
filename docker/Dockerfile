########################################################################
# Dockerfile for Oracle JDK 8 on Ubuntu 14.04
# contains following R versions:
#   - 3.4.1
#   - 3.3.3
#   - 3.2.5
#   - 3.1.3
#   - 3.0.3
#   - installed R versions can be overriden by setting the R_VERSIONS build arg
########################################################################

# pull base image
FROM ubuntu:16.04

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG R_VERSIONS='3.4.1,3.3.3'
ARG PYTHON_VERSIONS='3.6,3.5,2.7'

# Initialize apt sources
RUN \
  echo 'DPkg::Post-Invoke {"/bin/rm -f /var/cache/apt/archives/*.deb || true";};' | tee /etc/apt/apt.conf.d/no-cache && \
  echo "deb http://ap-northeast-1.ec2.archive.ubuntu.com/ubuntu xenial main universe" >> /etc/apt/sources.list && \
  echo "deb http://cran.mtu.edu/bin/linux/ubuntu xenial/" >> /etc/apt/sources.list.d/cran.list && \
  apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 51716619E084DAB9 && \
  apt-get update -q -y && \
  apt-get dist-upgrade -y && \
  apt-get clean && \
  rm -rf /var/cache/apt/*

# Install packages
RUN \
  DEBIAN_FRONTEND=noninteractive apt-get install -y wget curl s3cmd libffi-dev libxml2-dev libssl-dev \
  libcurl4-openssl-dev libfreetype6 libfreetype6-dev libfontconfig1 libfontconfig1-dev build-essential chrpath \
  libssl-dev libxft-dev git unzip python-pip python-dev python-virtualenv libmysqlclient-dev texlive \
  texlive-fonts-extra texlive-htmlxml python3 python3-dev python3-pip python3-virtualenv software-properties-common \
  python-software-properties texinfo texlive-bibtex-extra texlive-formats-extra texlive-generic-extra && \
  apt-get -y build-dep r-base

# Add repositories
RUN \
  add-apt-repository -y ppa:webupd8team/java && \
  add-apt-repository -y ppa:deadsnakes && \
  apt-get update -q -y

# Install Java
RUN \
  echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y oracle-java8-installer

# Copy required scripts
COPY scripts/install_R_version scripts/install_R_versions scripts/install_python_versions /usr/sbin/
RUN \
  chmod 700 /usr/sbin/install_R_version && \
  chmod 700 /usr/sbin/install_R_versions && \
  chmod 700 /usr/sbin/install_python_versions

# Install Python
RUN \
  /usr/sbin/install_python_versions

# Install nodeJS
RUN \
  curl -sL https://deb.nodesource.com/setup_7.x | bash - && \
  apt-get update -q -y && \
  apt-get install -y nodejs

# Install from source dependencies
RUN \
  wget https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2 && \
  tar xvjf phantomjs-2.1.1-linux-x86_64.tar.bz2 -C /usr/local/share/ && \
  rm phantomjs-2.1.1-linux-x86_64.tar.bz2 && \
  ln -sf /usr/local/share/phantomjs-2.1.1-linux-x86_64/bin/phantomjs /usr/local/bin && \
  apt-get clean

# Install R with different versions
RUN \
  /usr/sbin/install_R_versions
ENV PATH ${PATH}:/usr/local/R/current/bin/

# Expose ports for services
EXPOSE 54321
EXPOSE 8080

CMD \
  ["/bin/bash"]
