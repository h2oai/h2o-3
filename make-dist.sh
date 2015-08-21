#!/bin/bash

#
# Prepares distribution package which can be uploaded into s3
# Called by 'gradle makeH2oDevDist'
#

set -e
set -x

# Set common variables.
TOPDIR=$(cd `dirname $0` && pwd)
HADOOP_VERSIONS="cdh5.2 cdh5.3 cdh5.4.2 hdp2.1 hdp2.2 mapr3.1.1 mapr4.0.1"

function make_zip_common {
  PROJECT_BASE=$1
  IMAGEDIR=$2

  mkdir $IMAGEDIR/R
  cp h2o-r/R/src/contrib/h2o_${PROJECT_VERSION}.tar.gz $IMAGEDIR/R

  mkdir $IMAGEDIR/python

  cp h2o-py/dist/*whl $IMAGEDIR/python

  mkdir -p $IMAGEDIR/bindings/java
  cp h2o-java-rest-bindings/build/libs/h2o-java-rest-bindings-*.jar $IMAGEDIR/bindings/java

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
  cp h2o-hadoop/h2o-${HADOOP_VERSION}-assembly/build/libs/h2odriver.jar $IMAGEDIR
  cat h2o-dist/hadoop/README.txt | sed -e "s/SUBST_BRANCH_NAME/${BRANCH_NAME}/g" | sed -e "s/SUBST_BUILD_NUMBER/${BUILD_NUMBER}/g" > ${IMAGEDIR}/README.txt

  make_zip_common $PROJECT_BASE $IMAGEDIR
}

# Remove any previously created build directories.
rm -fr target
rm -fr h2o-dist/tmp

if [ -n "$DO_RELEASE" ]; then
  DO_RELEASE="-PdoRelease"
fi

# Run some required gradle tasks to produce final build output.
./gradlew $DO_RELEASE publish

# Create target dir, which is uploaded to s3.
mkdir target
echo ${PROJECT_VERSION} > target/project_version

# Create zip files and add them to target.
make_zip

if [ -z "$DO_FAST" ]; then
  for HADOOP_VERSION in $HADOOP_VERSIONS; do
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
rm -f h2o_${PROJECT_VERSION}.tar.gz
tar cvf h2o_${PROJECT_VERSION}.tar h2o
gzip h2o_${PROJECT_VERSION}.tar
rm -fr h2o
cd ../..
openssl dgst target/Rjar/h2o.jar | sed 's/.*= //' > target/Rjar/h2o.jar.md5

# Add Python dist to target.
mkdir -p target/Python

name=""
for f in h2o-py/dist/*
do
  name=${f##*/}
done

cp h2o-py/dist/*whl target/Python

cd h2o-py && sphinx-build -b html docs/ docs/docs/
cd ..

# Add Java bindings Jar to target.
mkdir -p target/bindings/java
cp -p h2o-java-rest-bindings/build/libs/*.jar target/bindings/java

# Add Maven repo to target.
mkdir target/maven
cp -rp build/repo target/maven

# Add documentation to target.
mkdir target/docs-website
mkdir target/docs-website/h2o-docs
mkdir target/docs-website/h2o-r
mkdir target/docs-website/h2o-py
mkdir target/docs-website/h2o-core
mkdir target/docs-website/h2o-algos
mkdir target/docs-website/h2o-scala
cp -rp h2o-docs/web/* target/docs-website/h2o-docs
cp -p h2o-r/R/h2o_package.pdf target/docs-website/h2o-r
cp -rp h2o-core/build/docs/javadoc target/docs-website/h2o-core
cp -rp h2o-algos/build/docs/javadoc target/docs-website/h2o-algos
cp -rp h2o-py/docs/docs/ target/docs-website/h2o-py
cp -rp h2o-scala/build/docs/scaladoc target/docs-website/h2o-scala

# Copy content of distribution site
cp h2o-dist/* target/ 2>/dev/null || true
# Create index file.
cat h2o-dist/index.html | sed -e "s/SUBST_WHEEL_FILE_NAME/${name}/g" | sed -e "s/SUBST_PROJECT_VERSION/${PROJECT_VERSION}/g" | sed -e "s/SUBST_LAST_COMMIT_HASH/${LAST_COMMIT_HASH}/g" > target/index.html

