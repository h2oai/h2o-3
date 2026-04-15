########################################################################
# Dockerfile for JDK on Ubuntu 24.04 LTS
########################################################################

# pull base image
FROM ubuntu:24.04

# maintainer details
LABEL maintainer="h2o.ai"

# add a post-invoke hook to dpkg which deletes cached deb files
# update the sources.list
# update/dist-upgrade
# clear the caches


RUN \
  echo 'DPkg::Post-Invoke {"/bin/rm -f /var/cache/apt/archives/*.deb || true";};' | tee /etc/apt/apt.conf.d/no-cache && \
  apt-get update -q -y && \
  apt-get dist-upgrade -y && \
  apt-get clean && \
  rm -rf /var/cache/apt/* && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates wget unzip openjdk-8-jdk software-properties-common && \
  add-apt-repository ppa:deadsnakes/ppa -y && \
  apt-get update -q -y && \
  apt-get install -y python3.11 python3.11-distutils python3.11-dev && \
  wget https://bootstrap.pypa.io/get-pip.py && \
  python3.11 get-pip.py && \
  rm get-pip.py && \
  update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1 && \
  python3.11 -m pip install scikit-learn pandas numpy matplotlib requests tabulate && \
  apt-get clean

# Fetch h2o latest_stable
RUN \
  wget http://h2o-release.s3.amazonaws.com/h2o/latest_stable -O latest && \
  wget -i latest -O /opt/h2o.zip && \
  unzip -d /opt /opt/h2o.zip && \
  rm /opt/h2o.zip && \
  cd /opt && \
  cd `find . -name 'h2o.jar' | sed 's/.\///;s/\/h2o.jar//g'` && \
  cp h2o.jar /opt && \
  python3.11 -m pip install `find . -name "*.whl"` && \
  printf '#!/bin/bash\ncd /home/h2o\n./start-h2o-docker.sh\n' > /start-h2o-docker.sh && \
  chmod +x /start-h2o-docker.sh

RUN \
  useradd -m -c "h2o.ai" h2o

USER h2o

# Get Content
RUN \
  cd && \
  wget https://raw.githubusercontent.com/h2oai/h2o-3/master/docker/start-h2o-docker.sh && \
  chmod +x start-h2o-docker.sh

# Define a mountable data directory
#VOLUME \
#  ["/data"]

# Define the working directory
WORKDIR \
  /home/h2o

EXPOSE 54321
EXPOSE 54322

#ENTRYPOINT ["java", "-Xmx4g", "-jar", "/opt/h2o.jar"]
# Define default command

CMD \
  ["/bin/bash"]
