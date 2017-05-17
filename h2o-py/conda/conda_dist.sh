#!/bin/bash

# Configure to automatically upload created conda package
# conda config --set anaconda_upload yes

# Grab the Project Version
PROJECT_VERSION=`cat ../../h2o-docs/src/product/project_version`

# Subsitute __version__ to build version
sed -i '' "s/SUBST_PROJECT_VERSION/${PROJECT_VERSION}/g" ../h2o/__init__.py

# Create conda package
conda build h2o

# Convert conda package for other platforms
conda convert ~/anaconda3/conda-bld/osx-64/h2o-${PROJECT_VERSION}-0.tar.bz2 -p all

# Upload the different builds
BUILD_NUMBER=h2o-${PROJECT_VERSION}-0.tar.bz2
echo $BUILD_NUMBER

anaconda upload linux-32/${BUILD_NUMBER}
anaconda upload linux-64/${BUILD_NUMBER}
anaconda upload win-32/${BUILD_NUMBER}
anaconda upload win-64/${BUILD_NUMBER}

sed -i '' "s/${PROJECT_VERSION}/SUBST_PROJECT_VERSION/g" ../h2o/__init__.py
