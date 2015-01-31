#!/bin/bash

#
# Prepares distribution package which can be uploaded into s3
# Called by 'gradle makeH2oDevDist'
#

set -e
set -x

# Set common directory variables.
TOPDIR=$(cd `dirname $0` && pwd)
IMAGEDIR=${TOPDIR}/h2o-dist/tmp/h2o-dev-${PROJECT_VERSION}

# Remove any previously created build directories.
rm -fr target
rm -fr h2o-dist/tmp



# Create image dir, which contains what is in the zip file.
cd $TOPDIR
mkdir -p $IMAGEDIR

cp build/h2o.jar $IMAGEDIR

mkdir $IMAGEDIR/R
cp h2o-r/R/src/contrib/h2o_${PROJECT_VERSION}.tar.gz $IMAGEDIR/R

mkdir -p $IMAGEDIR/hadoop/cdh5
cp -p h2o-hadoop/build/libs/h2o-hadoop.jar $IMAGEDIR/hadoop/cdh5

cd $IMAGEDIR/..
zip -r h2o-dev-${PROJECT_VERSION}.zip h2o-dev-${PROJECT_VERSION}



# Create target dir, which is uploaded to s3.
cd $TOPDIR
mkdir target

# Add zip file to target.
mv $IMAGEDIR/../h2o-dev-${PROJECT_VERSION}.zip ${TOPDIR}/target

# Add R CRAN structure to target.
mkdir -p target/R/src
cp -rp h2o-r/R/src/contrib target/R/src

# Add Python dist to target.
mkdir -p target/Python

name=""
for f in h2o-py/dist/*
do
  name=${f##*/}
done

cp h2o-py/dist/*whl target/Python

# Add documentation to target.
mkdir target/docs-website
mkdir target/docs-website/h2o-r
mkdir target/docs-website/h2o-core
mkdir target/docs-website/h2o-algos
cp -rp build/docs/REST target/docs-website
cp -p h2o-r/R/h2o_package.pdf target/docs-website/h2o-r
cp -rp h2o-core/build/docs/javadoc target/docs-website/h2o-core
cp -rp h2o-algos/build/docs/javadoc target/docs-website/h2o-algos



# Create index file.
cat h2o-dist/index.html | sed -e "s/SUBST_WHEEL_FILE_NAME/${name}/g" | sed -e "s/SUBST_PROJECT_VERSION/${PROJECT_VERSION}/g" | sed -e "s/SUBST_LAST_COMMIT_HASH/${LAST_COMMIT_HASH}/g" > target/index.html
