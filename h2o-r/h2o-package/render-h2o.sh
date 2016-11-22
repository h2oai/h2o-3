#!/bin/bash
set -e

### Render h2o R package from h2o-3 as a standalone R package h2o
# script will download recent nightly h2o.jar so you should always rebase your h2o package to recent master
# it will adjust your h2o package version BUILD_NUMBER to recent available nightly h2o.jar version
# edit H2O_VERSION variable for expected h2o release
# requires h2o-3 at least in bedc3e8e0f8b93a3a1778121eba319cb14deafc9 (18 Nov 2016)

## usage
# cd h2o-r/h2o-package
# ./render-h2o.sh

## dependencies
# git
# curl
# awk
# unzip
# R
# roxygen2 R pkg

export H2O_VERSION="3.11.0"
export PROJECT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

mkdir -p man inst/java

export JAR_BUILD_NUMBER=`curl -s http://h2o-release.s3.amazonaws.com/h2o/master/latest | head -1`
# download nightly h2o.jar - comment below to re-use h2o.jar from ./inst/java/h2o.jar
export JAR_BRANCH_ID=`curl -s http://h2o-release.s3.amazonaws.com/h2o/master/$JAR_BUILD_NUMBER/index.html | grep "def h2oProjectVersion" | tail -1 | awk '{print $4}' | tr -d '"' | tr -d '</br>'`
export JAR_DOWNLOAD_URL="http://h2o-release.s3.amazonaws.com/h2o/master/$JAR_BUILD_NUMBER/h2o-$JAR_BRANCH_ID.zip"
echo "Downloading recent h2o.jar build number: $JAR_BUILD_NUMBER"
curl -sS $JAR_DOWNLOAD_URL -o h2o-jar.zip
unzip -q -o -j "h2o-jar.zip" "h2o-$H2O_VERSION.$JAR_BUILD_NUMBER/h2o.jar" -d "inst/java"
rm h2o-jar.zip

# required to complete roxygen2::roxygenise which calls .h2o.downloadJar() when parsing zzz.R
export H2O_JAR_PATH=$(pwd)"/inst/java/h2o.jar"

# BUILD_NUMBER must match, otherwise R check will fail due to version mismatch, that means YOU should rebase your to recent master to stay close to nightly jar
export BUILD_NUMBER=$JAR_BUILD_NUMBER
export PROJECT_VERSION=${H2O_VERSION}.${BUILD_NUMBER}
export PROJECT_DATE=`date +%Y-%m-%d`

echo 'https://github.com/h2oai/h2o-3' > inst/source_code_repository_info.txt
echo $BUILD_NUMBER > inst/buildnum.txt
echo $BUILD_BRANCH > inst/branch.txt

# render DESCRIPTION, R documentation and NAMESPACE
Rscript -e 'file.copy("../h2o-DESCRIPTION.template", file<-"DESCRIPTION", overwrite=TRUE)->nul; subst<-function(var, file) {x<-gsub(paste0("SUBST_", var), Sys.getenv(var), readLines(file), fixed=TRUE); writeLines(x, file)}; sapply(c("PROJECT_VERSION","PROJECT_DATE"), subst, file)->nul'
Rscript -e 'file.copy("../h2o-package.template", file<-"man/h2o-package.Rd", overwrite=TRUE)->nul; subst<-function(var, file) {x<-gsub(paste0("SUBST_", var), Sys.getenv(var), readLines(file), fixed=TRUE); writeLines(x, file)}; sapply(c("PROJECT_VERSION","PROJECT_BRANCH","PROJECT_DATE"), subst, file)->nul'
Rscript -e 'roxygen2::roxygenise()'

echo -e "h2o R package content produced from templates. Check package using:\nR CMD build .\nR CMD check --run-dontrun --run-donttest --as-cran h2o_$PROJECT_VERSION.tar.gz"
