#!/usr/bin/env bash

#
# ubuntu/trusty64 provisioning for H2O-3
# 

# Java PPA
sudo add-apt-repository -y ppa:webupd8team/java
echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections

# R PPA
echo "deb http://cran.rstudio.com/bin/linux/ubuntu "$(lsb_release -sc)"/" | sudo tee /etc/apt/sources.list.d/cran.list
sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys E084DAB9
sudo add-apt-repository -y ppa:marutter/rdev

# Update
sudo apt-get update
sudo apt-get upgrade -y

# R devtools refuses to install without these packages in place.
sudo apt-get install -y libcurl4-openssl-dev libxml2-dev

# Git
sudo apt-get install -y git

# Java
sudo apt-get install -y oracle-java7-installer

# Python
sudo apt-get install -y \
  python-all \
  python-all-dev \
  python-all-dbg \
  python-pip

# Python packages
sudo pip install grip tabulate wheel numpy scikit-learn scipy requests future

# R
sudo apt-get install -y r-base-core=3.2.2-1trusty0

# R packages
export R_LIBS_USER=$HOME/R/libs
mkdir -p $R_LIBS_USER
R -e 'install.packages(c("RCurl","jsonlite","statmod","devtools","roxygen2","testthat"), dependencies=TRUE, repos="http://cran.rstudio.com/")'

# Node.js
curl -sL https://deb.nodesource.com/setup_0.12 | sudo bash -
sudo apt-get install -y nodejs

# Clone the H2O repo
git clone https://github.com/h2oai/h2o-3.git

# Build (skip tests)
cd h2o-3
./gradlew syncSmalldata
./gradlew build -x test
