#!/bin/bash

echo "Can use -p or -f arg, for incremental and full install, or use the commands and files below"
#****************************************************************************************************
CLEAN_R_STUFF=0
REMOVE_H2O_PACKAGES=0
INSTALL_R_PACKAGES=0
CREATE_FILES_ONLY=1
while getopts fp flag
do
    case $flag in
        f)
            echo "will delete ~/.Rlibrary, create ~/.Rprofile and ~/.Renviron, and reinstall R packages (not h2o package)"
            CLEAN_R_STUFF=1
            REMOVE_H2O_PACKAGES=1
            INSTALL_R_PACKAGES=1
            CREATE_FILES_ONLY=0
            ;;
        p)
            echo "delete h2o package. Check and if necessary, install R packages (not h2o package)"
            REMOVE_H2O_PACKAGES=1
            INSTALL_R_PACKAGES=1
            CREATE_FILES_ONLY=0
            ;;
        ?)
            exit
            ;;
    esac
done
shift $(( OPTIND - 1 ))  # shift past the last flag or argument
# echo remaining parameters to Bash are $*

#*******************************************************************************
echo "Checking that you have R, and the R version"
which R
R --version | egrep -i '(version|platform)'
echo ""

#*******************************************************************************
# don't always remove..other users may have stuff he doesn't want to re-install
cat <<!  > /tmp/init_R_stuff.sh
    echo "Rebuilding ~/.Renviron and ~/.Rprofile for $USER"
    # Set CRAN mirror to a default location
    rm -f ~/.Renviron
    rm -f ~/.Rprofile
    echo "options(repos = \"http://cran.stat.ucla.edu\")" > ~/.Rprofile
    echo "R_LIBS_USER=\"~/.Rlibrary\"" > ~/.Renviron
    rm -f -r ~/.Rlibrary
    mkdir -p ~/.Rlibrary
!
chmod +x /tmp/init_R_stuff.sh

if [ $CLEAN_R_STUFF -eq 1 ]
then 
    sh -x /tmp/init_R_stuff.sh
else
    echo "To remove and recreate your .Renviron/.Rprofile/.Rlibrary like jenkins, enter the next line at the command prompt"
    echo ""
    echo "    /tmp/init_R_stuff.sh"
    echo ""
    echo "Otherwise, I did nothing here, except create /tmp/init_R_stuff.sh"
    echo ""
fi

#*******************************************************************************
# removing .Rlibrary should have removed h2oWrapper
# but maybe it was installed in another library (site library)
# make sure it's removed, so the install installs the new (latest) one

rm -f /tmp/libPaths.cmd
if [[ $REMOVE_H2O_PACKAGES -eq 1 || $CREATE_FILES_ONLY -eq 1 ]]
then 
    cat <<!  >> /tmp/libPaths.cmd
.libPaths()
myPackages = rownames(installed.packages())
if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
# this will only remove from the first library in .libPaths()
# may need permission to remove from other libraries
# remove from all possible locations in .libPaths()
if ("h2o" %in% rownames(installed.packages())) { 
    remove.packages("h2o",.libPaths()[1]) 
    remove.packages("h2o",.libPaths()[2]) 
    remove.packages("h2o",.libPaths()[3]) 
    remove.packages("h2o",.libPaths()[4]) 
}
!
fi

if [[ $INSTALL_R_PACKAGES -eq 1 || $CREATE_FILES_ONLY -eq 1 ]]
then 
    cat <<!  >> /tmp/libPaths.cmd
# make the install conditional. Don't install if it's already there
# update if allready there?
usePackage <- function(p) {
    local({r <- getOption("repos"); r["CRAN"] <- "http://cran.us.r-project.org"; options(repos = r)})
    if (is.element(p, installed.packages()[,1])) {
        update.packages(p, dep = TRUE)
    }
    else {
        install.packages(p, dep = TRUE)
    }
    require(p, character.only = TRUE)
}

# what packages did the h2o_master_test need?
usePackage("R.utils")
usePackage("R.oo")
usePackage("R.methodsS3")
usePackage("RCurl")
usePackage("rjson")
usePackage("statmod")
usePackage("testthat")
usePackage("bitops")
usePackage("tools")
usePackage("LiblineaR")
usePackage("gdata")
usePackage("caTools")
usePackage("gplots")
usePackage("ROCR")
usePackage("digest")
usePackage("penalized")
usePackage("rgl")
usePackage("randomForest")

# these came from source('../findNSourceUtils.R')
usePackage("expm")
usePackage("Matrix")
usePackage("glmnet")
usePackage("survival")
usePackage("gbm")
# usePackage("splines")
usePackage("lattice")
# usePackage("parallel")
usePackage("RUnit")
usePackage("plyr")

# usePackage("h2o")
# usePackage("h2oRClient")
# usePackage("h2o", repos=(c("http://s3.amazonaws.com/h2o-release/h2o/master/1245/R", getOption("repos")))) 
# library(h2o)
!
fi
#****************************************************************************************************
# if Jenkins is running this, doing execute it..he'll execute it to logs for stdout/stderr
if [ $CREATE_FILES_ONLY -eq 0 ]
then
    R -f /tmp/libPaths.cmd
else
    echo "If you want to setup R packages the RUnit tests use, like jenkins..then enter the next line at the command prompt"
    echo "Doesn't cover h2o package. Okay for the Runit test to handle that"
    echo ""
    echo "    R -f /tmp/libPaths.cmd"
    echo ""
    echo "Otherwise, I did nothing here, except create /tmp/libPaths.cmd"
fi

echo "If RCurl didn't install, you probably need libcurl-devel. ('sudo yum install libcurl-devel' on centos). libcurl not enough?"
echo "On ubuntu:"
echo "sudo apt-get install libcurl4-openssl-dev"
echo ""
echo "Probably want these for liblinear etc"
echo "sudo apt-get install libblas3gf"
echo "sudo apt-get install libblas-doc"
echo "sudo apt-get install libblas-dev"
echo "sudo apt-get install liblapack3gf"
echo "sudo apt-get install liblapack-doc"
echo "sudo apt-get install liblapack-dev"

echo ""
echo "If rgl didn't install because of GL/gl.h in ubuntu, do this install first"
echo "sudo apt-get install r-base-dev xorg-dev libglu1-mesa-dev"
echo "or maybe"
echo "sudo apt-get build-dep r-cran-rgl"
echo ""
echo "If it complained about no package named 'h2o' you need to do a make"


