FROM ubuntu:18.04

RUN apt-get -y update && apt-get -y upgrade && \
    apt-get -y --no-install-recommends install \
        wget \
        curl \
        unzip \
        apt-utils \
        software-properties-common \
        net-tools \
        nginx \
        ca-certificates \
        build-essential

RUN \
  echo 'DPkg::Post-Invoke {"/bin/rm -f /var/cache/apt/archives/*.deb || true";};' | tee /etc/apt/apt.conf.d/no-cache && \
  echo "deb http://mirror.math.princeton.edu/pub/ubuntu xenial main universe" >> /etc/apt/sources.list && \
  apt-get update -q -y && \
  apt-get dist-upgrade -y && \
  apt-get clean && \
  rm -rf /var/cache/apt/* && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y wget unzip python-pip python-sklearn python-pandas python-numpy python-matplotlib software-properties-common python-software-properties
   
RUN apt-get install -y openjdk-8-jdk

RUN java -version

RUN apt-get -y install \
      python3-setuptools \
      python3-pip \
      python3-pandas \
      python3-numpy \
      python3-matplotlib \
      libxml2-dev \
      libssl-dev \
      libcurl4-openssl-dev \
      libgtk2.0-0 \
      iputils-ping

RUN wget http://h2o-release.s3.amazonaws.com/h2o/latest_stable -O latest && \
    wget --no-check-certificate -i latest -O /opt/h2o-latest.zip && \
    unzip -d /opt /opt/h2o-latest.zip && \
    rm /opt/h2o-latest.zip && \
    cd /opt && \
    mkdir program && \
    cd `find . -name 'h2o.jar' | sed 's/.\///;s/\/h2o.jar//g'` && \
    cp h2o.jar /opt/program && \
    touch /opt/program/flatfile.txt

RUN /usr/bin/pip3 install --upgrade --force-reinstall pip==9.0.3 && \
    /usr/bin/pip3 install numpy scipy scikit-learn pandas flask gevent gunicorn requests tabulate colorama future && \
    /usr/bin/pip3 install -f http://h2o-release.s3.amazonaws.com/h2o/latest_stable_Py.html h2o --user

# Set some environment variables. PYTHONUNBUFFERED keeps Python from buffering our standard
# output stream, which means that logs can be delivered to the user quickly. PYTHONDONTWRITEBYTECODE
# keeps Python from writing the .pyc files which are unnecessary in this case. We also update
# PATH so that the train and serve programs are found when the container is invoked.

ENV PYTHONUNBUFFERED=TRUE
ENV PYTHONDONTWRITEBYTECODE=TRUE
ENV PATH="/opt/program:${PATH}"

# Set up the program in the image
COPY gbm_scripts /opt/program
RUN chmod +x /opt/program/startup_h2o_cluster.sh
WORKDIR /opt/program

RUN ld -v
RUN ldd --version
