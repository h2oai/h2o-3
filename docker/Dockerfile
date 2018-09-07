########################################################################
# Dockerfile for Oracle JDK 8 on Ubuntu 14.04
# contains following R versions:
#   - 3.4.1
#   - 3.3.3
#   - installed R versions can be overriden by setting the R_VERSIONS build arg
# contains following Python versions:
#   - 2.7
#   - 3.5
#   - 3.6
#   - installed Python versions can be overriden by setting the PYTHON_VERSIONS build arg
########################################################################

# pull base image
FROM ubuntu:16.04

# maintainer details
MAINTAINER h2oai "h2o.ai"

ARG R_VERSIONS='3.4.1,3.3.3'
ARG PYTHON_VERSIONS='3.6,3.5,2.7'
ARG JAVA_VERSIONS='7,8,10'
ARG JENKINS_UID='2117'
ARG JENKINS_GID='2117'
ARG H2O_BRANCH='master'

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
  python-software-properties texinfo texlive-bibtex-extra texlive-formats-extra texlive-generic-extra nodejs-legacy npm && \
  apt-get -y build-dep r-base

# Add repositories
RUN \
  add-apt-repository -y ppa:deadsnakes && \
  apt-get update -q -y

# Set required env vars and install Java 8
COPY jdk1.7.0_80.zip /usr/opt/java-7-80/
COPY scripts/install_java_versions scripts/install_java_version /usr/sbin/
RUN \
  chmod 700 /usr/sbin/install_java_versions && \
  chmod 700 /usr/sbin/install_java_version && \
  sync && \
  /usr/sbin/install_java_versions
ENV \
  JAVA_HOME=/usr/lib/jvm/java-current-oracle \
  PATH=/usr/lib/jvm/java-current-oracle/bin:${PATH}

# Add the Jenkins user
RUN \
  groupadd -g ${JENKINS_GID} jenkins && \
  adduser --uid ${JENKINS_UID} -gid ${JENKINS_GID} --disabled-password --gecos "" jenkins

# Install from source dependencies
RUN \
  wget https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2 && \
  tar xvjf phantomjs-2.1.1-linux-x86_64.tar.bz2 -C /usr/local/share/ && \
  rm phantomjs-2.1.1-linux-x86_64.tar.bz2 && \
  ln -sf /usr/local/share/phantomjs-2.1.1-linux-x86_64/bin/phantomjs /usr/local/bin && \
  wget https://github.com/jgm/pandoc/releases/download/2.1.1/pandoc-2.1.1-linux.tar.gz && \
  tar -xvzf pandoc-2.1.1-linux.tar.gz --strip-components 1 -C /usr/local/ && \
  which pandoc && \
  rm -rf pandoc-2.1.1-linux.tar.gz && \
  apt-get clean && \
  apt-get autoremove && \
  apt-get autoclean

# Copy required scripts
COPY scripts/install_R_version scripts/install_R_versions scripts/install_python_versions /usr/sbin/
RUN \
  chmod 700 /usr/sbin/install_R_version && \
  chmod 700 /usr/sbin/install_R_versions && \
  chmod 700 /usr/sbin/install_python_versions

# Install Python
RUN \
  /usr/sbin/install_python_versions

# Install R with different versions
RUN \
  /usr/sbin/install_R_versions
ENV PATH ${PATH}:/usr/local/R/current/bin/


# Copy H2O-3 build script and create required folders
COPY scripts/build-h2o-3 /tmp
RUN \
  mkdir -p /opt/h2o-3 /gradle-user-home && \
  chmod +x /tmp/build-h2o-3 && \
  chown jenkins:jenkins /tmp/build-h2o-3 /opt/h2o-3 /gradle-user-home

# Set GRADLE USER env var
ENV GRADLE_USER_HOME '/gradle-user-home'
ENV GRADLE_OPTS '-Dorg.gradle.daemon=false'
ENV LANG 'C.UTF-8'
ENV JAVA_VERSION '8'

# build h2o-3 as custom user
USER jenkins
RUN \
  /tmp/build-h2o-3

USER root
# remove the tmp and h2o-3 files
# remove the /gradle-user-home/daemon as well since it won't break caches and the folder is messed up
RUN \
  rm -rf /tmp/build-h2o-3 /gradle-user-home/daemon

# Install s3cmd
ENV S3_CMD_VERSION '2.0.1'
RUN \
  cd /opt/ && \
  wget http://netix.dl.sourceforge.net/project/s3tools/s3cmd/${S3_CMD_VERSION}/s3cmd-${S3_CMD_VERSION}.tar.gz && \
  tar xvzf s3cmd-${S3_CMD_VERSION}.tar.gz && \
  cd s3cmd-${S3_CMD_VERSION} && \
  python setup.py install

# Install conda
RUN \
  curl -sSL https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -o /tmp/miniconda.sh && \
  bash /tmp/miniconda.sh -bfp /usr/local && \
  rm -rf /tmp/miniconda.sh && \
  conda install -y anaconda-client conda-build && \
  conda update conda && \
  conda clean --all --yes
ENV PATH /opt/conda/bin:$PATH

# Remove unnecessary files
RUN \
    rm -rf /usr/share/man /usr/share/doc && \
    apt-get remove -y --purge man-db

# Expose ports for services
EXPOSE 54321
EXPOSE 8080

CMD \
  ["/bin/bash"]
