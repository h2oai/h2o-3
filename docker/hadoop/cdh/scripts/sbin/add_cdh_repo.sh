#!/bin/bash -ex

VERSION=$1
REPO_VERSION=$2
USERNAME=$3
PASSWORD=$4

case ${VERSION} in
  5*)
    echo -e "# Packages for Cloudera's Distribution of Hadoop, Version 5\n\
deb [arch=amd64] http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n\
deb-src http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh trusty-cdh${VERSION} contrib\n" \
      > /etc/apt/sources.list.d/cloudera.list
    wget --http-user=${USERNAME} --http-passwd=${PASSWORD} \
      http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key \
      -O archive.key
    ;;
  6.*)
    echo -e "# Packages for Cloudera's Distribution of Hadoop, Version ${REPO_VERSION}\n\
deb [arch=amd64] http://archive.cloudera.com/cdh6/${REPO_VERSION}/ubuntu1604/apt xenial-cdh${REPO_VERSION} contrib\n" \
      > /etc/apt/sources.list.d/cloudera.list && \
    wget --http-user=${USERNAME} --http-passwd=${PASSWORD} \
      https://archive.cloudera.com/cdh6/${REPO_VERSION}/ubuntu1604/apt/archive.key \
      -O archive.key
    ;;
  *)
    echo "Version ${VERSION} not supported"
    ;;
esac
