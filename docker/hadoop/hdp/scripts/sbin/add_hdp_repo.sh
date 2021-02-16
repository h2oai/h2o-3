#! /bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

function print_red {
  echo -e "${RED}${1}${NC}"
}

function print_green {
  echo -e "${GREEN}${1}${NC}"
}

case "${1}" in
  3.1)
    major_version="3"
    hdp_version="3.1.0.0"
    ubuntu_repo_version="14"
    ;;
  3.0)
    major_version="3"
    hdp_version="3.0.1.0"
    ubuntu_repo_version="14"
    ;;
  2.6)
    major_version="2"
    hdp_version="2.6.1.0"
    ubuntu_repo_version="14"
    ;;
  2.5)
    major_version="2"
    hdp_version="2.5.6.0"
    ubuntu_repo_version="14"
    ;;
  2.4)
    major_version="2"
    hdp_version="2.4.3.0"
    ubuntu_repo_version="14"
    ;;
  2.3)
    major_version="2"
    hdp_version="2.3.6.0"
    ubuntu_repo_version="14"
    ;;
  2.2)
    major_version="2"
    hdp_version="2.2.9.0"
    ubuntu_repo_version="12"
    ;;
  *)
    print_red "HDP version '${1}' not supported"
    exit 1
esac

export HDP_VERSION=${hdp_version}
export UBUNTU_REPO_VERSION=${ubuntu_repo_version}

echo -e "Building for HDP version ${GREEN}${hdp_version}${NC}"

wget http://archive.cloudera.com/p/HDP/${major_version}.x/${hdp_version}/ubuntu${ubuntu_repo_version}/hdp.list \
  -O /etc/apt/sources.list.d/hdp.list
gpg --keyserver keyserver.ubuntu.com --recv-keys B9733A7A07513CAD
gpg -a --export 07513CAD | apt-key add -
