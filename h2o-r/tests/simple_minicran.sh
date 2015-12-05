#!/bin/bash

#
# First run this tool inside a bash shell to build the minicran.
# The Windows GIT client comes with a nice bash shell.
#
#
# Then from inside R:
#
# options(repos = "file:///C:/Users/h2o/Desktop/tmp/minicran")
# install.packages("h2o")
# library(h2o)
# h = h2o.init()
# demo(h2o.glm)
#

set -x
set -e

r_version=3.0

# Windows
# -------
os=windows
ext=.zip

# Mac
# ---
# os=macosx
# ext=.tgz

branch=rel-jacobi
major=2
minor=2
incremental=0
build=6
project_version=${major}.${minor}.${incremental}.${build}

# binary dir
d=minicran/bin/${os}/contrib/${r_version}
# source dir
sd=minicran/src/contrib
mkdir minicran
mkdir -p ${d}
mkdir -p ${sd}

f=PACKAGES
LIST=${d}/${f}
curl -o ${f} http://cran.r-project.org/bin/${os}/contrib/${r_version}/${f}
mv ${f} ${LIST}.tmp

curl -o ${f} http://h2o-release.s3.amazonaws.com/h2o/${branch}/${build}/R/bin/${os}/contrib/${r_version}/${f}
cat ${f} > ${LIST}.tmp2
echo "" >> ${LIST}.tmp2
cat ${LIST}.tmp >> ${LIST}.tmp2
mv ${LIST}.tmp2 ${LIST}
rm ${LIST}.tmp
rm ${f}
gzip -c ${LIST} > ${LIST}.gz
cp ${LIST} ${sd}
cp ${LIST} ${sd}

f=h2o_${project_version}${ext}
curl -o ${f} http://h2o-release.s3.amazonaws.com/h2o/${branch}/${build}/R/bin/${os}/contrib/${r_version}/${f}
mv ${f} ${d}/${f}

f=bitops_1.0-6${ext}
curl -o ${f} http://cran.r-project.org/bin/${os}/contrib/${r_version}/${f}
mv ${f} ${d}/${f}

f=RCurl_1.95-4.1${ext}
curl -o ${f} http://cran.r-project.org/bin/${os}/contrib/${r_version}/${f}
mv ${f} ${d}/${f}

f=rjson_0.2.13${ext}
curl -o ${f} http://cran.r-project.org/bin/${os}/contrib/${r_version}/${f}
mv ${f} ${d}/${f}

f=statmod_1.4.18${ext}
curl -o ${f} http://cran.r-project.org/bin/${os}/contrib/${r_version}/${f}
mv ${f} ${d}/${f}

