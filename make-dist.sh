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

# Create target dir, which is uploaded to s3.
cd $TOPDIR
rm -fr target
mkdir target
rm -fr h2o-dist/tmp
mkdir -p $IMAGEDIR
cp build/h2o.jar $IMAGEDIR
cd $IMAGEDIR/..
zip -r h2o-dev-${PROJECT_VERSION}.zip h2o-dev-${PROJECT_VERSION}
mv h2o-dev-${PROJECT_VERSION}.zip ${TOPDIR}/target
cd $TOPDIR

# Create index file.
cat h2o-dist/index.html | sed -e "s/SUBST_PROJECT_VERSION/${PROJECT_VERSION}/g" > target/index.html
