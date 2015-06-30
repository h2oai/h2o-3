#options(echo=F)
options(repos = "http://cran.cnr.berkeley.edu/")

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
              "survival", "gbm", "lattice", "RUnit", "plyr", "devtools", "roxygen2", "flexclust", "e1071", "ade4")

invisible(lapply(packages, usePackage))

library(R.utils)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

user <- Sys.getenv()["USER"]
if( !is.na(user) && user == "jenkins") {
  print("JENKINS R_LIBS_USER PATH SET TO: ")
  print( Sys.getenv()["R_LIBS_USER"] )
  print("R .libPaths(): ")
  print( .libPaths() )
}

if( "h2o" %in% rownames(installed.packages()) ) {
  lapply(.libPaths(), function(p) {
    tryCatch(remove.packages("h2o", p), error=function(e) { paste0("No h2o package in libPath: ", p) })
  })
}
failed <<- F
tryCatch(library(h2o), error = function(e) {failed <<- T})
if (! failed) {
    stop("Failed to remove h2o library")
}

h2o_r_package_file <- NULL

args <- commandArgs(trailingOnly = TRUE)

if( length(args) == 1) {
  print("")
  print("Got args:")
  print(args)
  print("")
  arr = strsplit(args, '\\.')[[1]]
  lastidx = length(arr)
  suffix = arr[lastidx]
  if (suffix == "gz") {
    h2o_r_package_file = args
  }
  if( is.null(h2o_r_package_file) )
    stop("Could not find the h2o R package file!")
  install.packages(h2o_r_package_file, repos = NULL, type = "source")
} else {

  raw_dir_to_search = "../../R/src/contrib"

  if (! file.exists(raw_dir_to_search)) {
      stop("R build directory does not exist, you probably need to do a gradle build")
  }
  dir_to_search = normalizePath(raw_dir_to_search)
  files = dir(dir_to_search, pattern="h2o.*.gz")
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
}

library(h2o)
