#!/bin/bash

# After building H2O with ./gradlew build
# run this script to install the new R and Python clients.

pip uninstall -y h2o
pip install ../h2o-py/build/dist/h2o-*-py2.py3-none-any.whl
R CMD REMOVE h2o
R CMD INSTALL ../h2o-r/R/src/contrib/h2o*.tar.gz
