#!/bin/bash

#
# Prepares distribution package which can be uploaded into s3
# Called by 'gradle buildH2oDevDist'
#

set -e
set -x

# Set common variables.
TOPDIR=$(cd `dirname $0` && pwd)
ALL_HADOOP_VERSIONS="cdh5.4 cdh5.5 cdh5.6 cdh5.7 cdh5.8 cdh5.9 cdh5.10 cdh5.13 cdh5.14 cdh5.15 cdh5.16 \
cdh6.0 cdh6.1 cdh6.2 cdh6.3 cdp7.0 cdp7.1 cdp7.2 \
hdp2.2 hdp2.3 hdp2.4 hdp2.5 hdp2.6 hdp3.0 hdp3.1 \
mapr4.0 mapr5.0 mapr5.1 mapr5.2 mapr6.0 mapr6.1 mapr6.2 mapr7.0 \
iop4.2 emr6.10" 

function make_zip_common {
  PROJECT_BASE=$1
  IMAGEDIR=$2

  mkdir $IMAGEDIR/R
  cp h2o-r/R/src/contrib/h2o_${PROJECT_VERSION}.tar.gz $IMAGEDIR/R
  cp h2o-r/build/client/h2o_${PROJECT_VERSION}.tar.gz $IMAGEDIR/R/h2o_client_${PROJECT_VERSION}.tar.gz
  cp h2o-r/dist-readme.txt $IMAGEDIR/R/README.txt

  mkdir $IMAGEDIR/python

  cp h2o-py/build/dist/*whl $IMAGEDIR/python
  cp h2o-py/build/client/dist/*whl $IMAGEDIR/python
  cp h2o-py/dist-readme.txt $IMAGEDIR/python/README.txt

  mkdir -p $IMAGEDIR/bindings/java
  cp h2o-bindings/build/distributions/h2o-bindings-*.zip $IMAGEDIR/bindings/java

  cd $IMAGEDIR/..
  zip -r ${PROJECT_BASE}.zip ${PROJECT_BASE}
  cd $TOPDIR

  # Add zip file to target.
  mv $IMAGEDIR/../${PROJECT_BASE}.zip ${TOPDIR}/target
}

function make_zip {
  PROJECT_BASE=h2o-${PROJECT_VERSION}
  IMAGEDIR=${TOPDIR}/h2o-dist/tmp/${PROJECT_BASE}

  mkdir -p $IMAGEDIR
  cp build/h2o.jar $IMAGEDIR

  make_zip_common $PROJECT_BASE $IMAGEDIR
}

function make_hadoop_zip {
  HADOOP_VERSION=$1
  PROJECT_BASE=h2o-${PROJECT_VERSION}-${HADOOP_VERSION}
  IMAGEDIR=${TOPDIR}/h2o-dist/tmp/${PROJECT_BASE}

  mkdir -p $IMAGEDIR
  mkdir $IMAGEDIR/docs
  cp -r target/docs-website/* $IMAGEDIR/docs/ 
  cp h2o-hadoop-*/h2o-${HADOOP_VERSION}-assembly/build/libs/h2odriver.jar $IMAGEDIR
  cat h2o-dist/hadoop/README.txt | sed -e "s/SUBST_BRANCH_NAME/${BRANCH_NAME}/g" | sed -e "s/SUBST_BUILD_NUMBER/${BUILD_NUMBER}/g" > ${IMAGEDIR}/README.txt
  cat h2o-dist/hadoop/distribution.info | sed -e "s/H2O_VERSION/${PROJECT_VERSION}/g" | sed -e "s/HADOOP_VERSION/${HADOOP_VERSION}/g" > ${IMAGEDIR}/distribution.info

  make_zip_common $PROJECT_BASE $IMAGEDIR
}

# Remove any previously created build directories.
rm -fr target
rm -fr h2o-dist/tmp

if [ -n "$DO_RELEASE" ]; then
  DO_RELEASE="-PdoRelease"
fi

# Run some required gradle tasks to produce final build output.
./gradlew booklets
./gradlew $DO_RELEASE publish
# TODO improve (gradle calls script which then calls gradle, that creates problems with gradle daemon)

# Generate Py Docs
(cd h2o-py && sphinx-build -b html docs/ docs/docs/)

# Generate R Docs
make -f scripts/jenkins/Makefile.jenkins r-generate-docs

# Build main h2o sphinx documentation.
(cd h2o-docs/src/product && sphinx-build -b html -d _build/doctrees . _build/html)

# Create target dir, which is uploaded to s3.
mkdir target
echo ${PROJECT_VERSION} > target/project_version

# Add documentation to target.
mkdir target/docs-website
mkdir target/docs-website/h2o-docs
mkdir target/docs-website/h2o-docs/booklets
mkdir target/docs-website/h2o-r
mkdir target/docs-website/h2o-py
mkdir target/docs-website/h2o-core
mkdir target/docs-website/h2o-algos
mkdir target/docs-website/h2o-genmodel
cp -rp h2o-docs/src/front/* target/docs-website
cp -rp h2o-docs/src/product/_build/html/* target/docs-website/h2o-docs
cp -rp h2o-docs/web/* target/docs-website/h2o-docs
cp -p h2o-docs/src/booklets/v2_2015/source/*.pdf target/docs-website/h2o-docs/booklets
cp -p h2o-r/R/h2o_package.pdf target/docs-website/h2o-r
cp -rp h2o-py/docs/docs target/docs-website/h2o-py
cp -rp h2o-r/h2o-package/docs target/docs-website/h2o-r
cp -rp h2o-core/build/docs/javadoc target/docs-website/h2o-core
cp -rp h2o-algos/build/docs/javadoc target/docs-website/h2o-algos
cp -rp h2o-genmodel/build/docs/javadoc target/docs-website/h2o-genmodel

# Create a zip file for the whole documentation and add to target.
(cd target && zip -r docs-website.zip docs-website)

# Create zip files and add them to target.
make_zip

if [ -z "${H2O_TARGET}" ]; then
  HADOOP_VERSIONS_TO_PROCESS=$ALL_HADOOP_VERSIONS;
else
  HADOOP_VERSIONS_TO_PROCESS="${H2O_TARGET//,/ }"
fi

if [ -z "$DO_FAST" ]; then
  for HADOOP_VERSION in $HADOOP_VERSIONS_TO_PROCESS; do
    make_hadoop_zip $HADOOP_VERSION
  done
fi

# Add R CRAN structure to target.
mkdir -p target/R/src
cp -rp h2o-r/R/src/contrib target/R/src

# Create shrunken Rcran CRAN source package with no h2o.jar file.
# Create Rjar directory for .h2o.downloadJar()
mkdir target/Rcran
mkdir target/Rjar
cd target/Rcran
cp -p ../R/src/contrib/h2o_${PROJECT_VERSION}.tar.gz .
tar zxvf h2o_${PROJECT_VERSION}.tar.gz
mv h2o/inst/java/h2o.jar ../Rjar
mkdir gaid
touch gaid/CRAN
jar -uf ../Rjar/h2o.jar gaid/CRAN
rm -rf gaid
JAR_MD5=`md5sum ../Rjar/h2o.jar | sed -r 's/(.{32}).*/\1/'`

rm -f h2o_${PROJECT_VERSION}.tar.gz
perl -pi -e "s/CURRENT_R_PACKAGE_MD5_CHECKSUM/${JAR_MD5}/"  h2o/R/connection.R
R CMD build h2o
rm -fr h2o
cd ../..
echo $JAR_MD5 > target/Rjar/h2o.jar.md5

# Add Python dist to target.
mkdir -p target/Python

name=""
for f in h2o-py/build/dist/*
do
  name=${f##*/}
done

client_name=""
for f in h2o-py/build/client/dist/*
do
  client_name=${f##*/}
done

cp h2o-py/build/dist/*whl target/Python
cp h2o-py/build/client/dist/*whl target/Python

# Add Java bindings Jar to target.
mkdir -p target/bindings/java
cp -p h2o-bindings/build/libs/*.jar target/bindings/java

# Add Minimal and Steam assembly Jars to target.
cp h2o-assemblies/minimal/build/libs/minimal.jar target/h2o-minimal-${PROJECT_VERSION}.jar
cp h2o-assemblies/steam/build/libs/steam.jar target/h2o-steam-${PROJECT_VERSION}.jar

# Add Maven repo to target.
mkdir target/maven
cp -rp build/repo target/maven

# Generate SHA256 from zip file
(cd target && sha256sum h2o-*.zip > sha256.txt)

# Copy content of distribution site
cp h2o-dist/* target/ 2>/dev/null || true

# Create index file.
cat h2o-dist/index.html \
  | sed -e "s/SUBST_WHEEL_FILE_NAME/${name}/g" \
  | sed -e "s/SUBST_CLIENT_WHEEL_FILE_NAME/${client_name}/g" \
  | sed -e "s/SUBST_PROJECT_VERSION/${PROJECT_VERSION}/g" \
  | sed -e "s/SUBST_LAST_COMMIT_HASH/${LAST_COMMIT_HASH}/g" \
  > target/index.html

# Create json metadata file.
cat h2o-dist/buildinfo.json \
  | sed -e "s/SUBST_WHEEL_FILE_NAME/${name}/g" \
  | sed -e "s/SUBST_BUILD_TIME_MILLIS/${BUILD_TIME_MILLIS}/g" \
  | sed -e "s/SUBST_BUILD_TIME_ISO8601/${BUILD_TIME_ISO8601}/g" \
  | sed -e "s/SUBST_BUILD_TIME_LOCAL/${BUILD_TIME_LOCAL}/g" \
  | sed -e "s/SUBST_PROJECT_VERSION/${PROJECT_VERSION}/g" \
  | sed -e "s/SUBST_BRANCH_NAME/${BRANCH_NAME}/g" \
  | sed -e "s/SUBST_BUILD_NUMBER/${BUILD_NUMBER}/g" \
  | sed -e "s/SUBST_LAST_COMMIT_HASH/${LAST_COMMIT_HASH}/g" \
  > target/buildinfo.json
