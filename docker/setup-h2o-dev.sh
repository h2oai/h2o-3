#!/bin/bash

# Setup Repos
echo 'DPkg::Post-Invoke {"/bin/rm -f /var/cache/apt/archives/*.deb || true";};' | tee /etc/apt/apt.conf.d/no-cache
echo "deb http://ap-northeast-1.ec2.archive.ubuntu.com/ubuntu xenial main universe" >> /etc/apt/sources.list
echo "deb http://cran.cnr.berkeley.edu/bin/linux/ubuntu xenial/" >> /etc/apt/sources.list.d/cran.list
apt-get update -q -y
apt-get dist-upgrade -y
apt-get clean
rm -rf /var/cache/apt/* 

apt-get install -y wget curl s3cmd libffi-dev libxml2-dev libssl-dev libcurl4-openssl-dev libfreetype6 libfreetype6-dev
apt-get install -y libfontconfig1 libfontconfig1-dev build-essential chrpath libssl-dev libxft-dev git unzip 
apt-get install -y python-pip python-dev python-virtualenv libmysqlclient-dev texlive texlive-fonts-extra 
apt-get install -y texlive-htmlxml python3 python3-dev python3-pip python3-virtualenv software-properties-common 
apt-get install -y python-software-properties texinfo texlive-bibtex-extra texlive-formats-extra texlive-generic-extra
apt-get install -y screen vim

# Install Java
add-apt-repository -y ppa:webupd8team/java
add-apt-repository -y ppa:graphics-drivers/ppa
apt-get update -q -y
echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections  
apt-get install -y oracle-java8-installer

# Install Nvidia
# apt-get purge nvidia-*
# apt-get install nvidia-381

# Install NodeJS
curl -sL https://deb.nodesource.com/setup_7.x | bash -
apt-get update -q -y
apt-get install -y nodejs

# Install R
apt-get install -y --force-yes r-base r-base-dev

# Install PhantomJS
wget https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2
tar xvjf phantomjs-2.1.1-linux-x86_64.tar.bz2 -C /usr/local/share/
rm phantomjs-2.1.1-linux-x86_64.tar.bz2
ln -sf /usr/local/share/phantomjs-2.1.1-linux-x86_64/bin/phantomjs /usr/local/bin
apt-get clean 

# Install python dependancies
wget https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-py/requirements.txt
/usr/bin/pip install --upgrade pip
/usr/bin/pip install -r requirements.txt
/usr/bin/pip3 install --upgrade pip
/usr/bin/pip3 install -r requirements.txt
rm requirements.txt 

# Install R dependancies
R -e 'chooseCRANmirror(graphics=FALSE, ind=49);install.packages(c("R.utils", "AUC", "Hmisc", "flexclust", "randomForest", "bit64", "HDtweedie", "RCurl", "jsonlite", "statmod", "devtools", "roxygen2", "testthat", "Rcpp", "fpc", "RUnit", "ade4", "glmnet", "gbm", "ROCR", "e1071", "ggplot2", "LiblineaR"))' 

## Workaround for LiblineaR problem
wget https://s3.amazonaws.com/h2o-r/linux/LiblineaR_1.94-2.tar.gz
R CMD INSTALL LiblineaR_1.94-2.tar.gz

