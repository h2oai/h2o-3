ARG GPU_SUFFIX=''
ARG FROM_VERSION
FROM harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-8-base${GPU_SUFFIX}:${FROM_VERSION}

RUN \
    echo "deb http://cloud.r-project.org/bin/linux/ubuntu xenial/" >> /etc/apt/sources.list.d/cran.list && \
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 51716619E084DAB9 && \
    sed -Ei 's/^# deb-src /deb-src /' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y \
        zip \
        python-pip && \
    apt-get -y build-dep r-base && \
    apt-get -y remove openjdk-8-jdk openjdk-8-jdk-headless openjdk-8-jre openjdk-8-jre-headless && \
    apt-get clean && \
    apt-get autoremove -y && \
    rm -rf /var/cache/apt/* && \
    pip install requests && \
    wget https://github.com/jgm/pandoc/releases/download/2.1.1/pandoc-2.1.1-linux.tar.gz && \
    tar -xvzf pandoc-2.1.1-linux.tar.gz --strip-components 1 -C /usr/local/ && \
    rm -rf pandoc-2.1.1-linux.tar.gz
