#options(echo=F)
options(repos = "http://cran.stat.ucla.edu")

usePackage<-
function(p) {
  if (is.element(p, installed.packages()[,1])) {

    update.packages(p, dep = TRUE)
  } else {
    install.packages(p, dep = TRUE)
  }
}

# what packages did the h2o_master_DEV_test need?
packages <- c("R.utils", "R.oo", "R.methodsS3", "RCurl", "rjson", "statmod", "testthat", "bitops", "tools", "LiblineaR",
              "gdata", "caTools", "ROCR", "digest", "penalized", "rgl", "randomForest", "expm", "Matrix", "glmnet",
              "survival", "gbm", "lattice", "RUnit", "plyr")

invisible(lapply(packages, usePackage))

library(R.utils)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("h2oR.R")
ipPort <- get_args(commandArgs(trailingOnly = TRUE))

if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }
failed <<- F
tryCatch(library(h2o), error = function(e) {failed <<- T})
if (! failed) {
    stop("Failed to remove h2o library")
}

h2o_r_package_file <- NULL
dir_to_search = normalizePath("../../R/src/contrib")
files = dir(dir_to_search)
for (i in 1:length(files)) {
    f = files[i]
    arr = strsplit(f, '\\.')[[1]]
    lastidx = length(arr)
    suffix = arr[lastidx]
    if (suffix == "gz") {
        h2o_r_package_file = f
        break
    }
}

install.packages(paste(dir_to_search, h2o_r_package_file, sep="/"), repos = NULL, type = "source")
library(h2o)
