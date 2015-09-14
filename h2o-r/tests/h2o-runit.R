#'
#' h2o-R Unit Test Utilities
#'

#'
#' Setup
#'
#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)
local({r <- getOption("repos"); r["CRAN"] <- "http://cran.us.r-project.org"; options(repos = r)})
if (!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils")
if (!"plyr" %in% rownames(installed.packages())) install.packages("plyr")
tryCatch(if (!"rgl" %in% rownames(installed.packages())) install.packages("rgl"), error = function(e) { print("Oops. Couldn't install `rgl` package...") })
if (!"randomForest" %in% rownames(installed.packages())) install.packages("randomForest")
if (!"AUC" %in% rownames(installed.packages())) install.packages("AUC")
if(!"flexclust" %in% rownames(installed.packages())) install.packages("flexclust")
if (!"HDtweedie" %in% rownames(installed.packages())) install.packages("HDtweedie")
library(R.utils)

PROJECT.ROOT <- "h2o-3"
SUBPROJECT.ROOT <- "h2o-r"

#'
#' Locate a file given the pattern <bucket>/<path/to/file>
#'
#' e.g. locate( "smalldata/iris/iris22.csv") returns the absolute path to iris22.csv
locate<-
function(pathStub, root.parent = NULL) {
  pathStub <- clean(pathStub)
  bucket <- pathStub[1]
  offset <- pathStub[-1]
  cur.dir <- getwd()
  #recursively ascend until `bucket` is found
  bucket.abspath <- path.compute(cur.dir, bucket, root.parent)
  if (length(offset) != 0) return(paste(c(bucket.abspath, offset), collapse = "/", sep = "/"))
  else return(bucket.abspath)
}

#'
#' Clean a path up: change \ -> /; remove starting './'; split
clean<-
function(p) {

  if (.Platform$file.sep == "/") {
    p <- gsub("[\\]", .Platform$file.sep, p)
    p <- unlist(strsplit(p, .Platform$file.sep))
  } else {
    p <- gsub("/", "\\\\", p)  # is this right?
    p <- unlist(strsplit(p, "\\\\"))
  }

  p
}

#'
#' Compute a path distance.
#'
#' We are looking for a directory `root`. Recursively ascend the directory structure until the root is found.
#' If not found, produce an error.
#'
#' @param cur.dir: the current directory
#' @param root: the directory that is being searched for
#' @param root.parent: if not null, then the `root` must have `root.parent` as immediate parent
#' @return: Return the absolute path to the root.
path.compute<-
function(cur.dir, root, root.parent = NULL) {

  parent.dir  <- dirname(cur.dir)
  parent.name <- basename(parent.dir)

  # root.parent is null
  if (is.null(root.parent)) {

    # first check if cur.dir is root
    if (basename(cur.dir) == root) return(normalizePath(cur.dir))

    # next check if root is in cur.dir somewhere
    if (root %in% dir(cur.dir)) return(normalizePath(paste(cur.dir, .Platform$file.sep, root, sep = "")))

    # the root is the parent
    if (parent.name == root) return(normalizePath(paste(parent.dir, .Platform$file.sep, root, sep = "")))

    # the root is h2o-dev, check the children here (and fail if `root` not found)
    if (parent.name == PROJECT.ROOT) {
      if (root %in% dir(parent.dir)) return(normalizePath(paste(parent.dir, .Platform$file.sep, root, sep = "")))
      else stop(paste("Could not find the dataset bucket: ", root, sep = "" ))
    }

  # root.parent is not null
  } else {

    # first check if cur.dir is root
    if (basename(cur.dir) == root && parent.name == root.parent) return(normalizePath(cur.dir))

    # next check if root is in cur.dir somewhere (if so, then cur.dir is the parent!)
    if (root %in% dir(cur.dir) && root.parent == basename(cur.dir)) return(normalizePath(paste(cur.dir, .Platform$file.sep, root, sep = "")))

    # the root is the parent
    if (parent.name == root && basename(dirname(parent.dir)) == root.parent) return(path.compute(parent.dir, root, root.parent)) #return(normalizePath(paste(parent.dir, "/", root, sep = "")))

    # fail if reach h2o-dev
    if (parent.name == PROJECT.ROOT) stop("Reached the root h2o-dev. Didn't find the bucket with the root.parent")
  }
  return(path.compute(parent.dir, root, root.parent))
}

#'
#' Source the /h2o-package/R/ directory
#'
src <-
function(ROOT.PATH) {
  to_src <- c("/classes.R", "/connection.R", "/constants.R", "/logging.R", "/communication.R", "/kvstore.R", "/exec.R", "/ops.R", "/frame.R", "/ast.R", "/astfun.R", "/import.R", "/parse.R", "/export.R", "/models.R", "/edicts.R", "/gbm.R","/glm.R", "/glrm.R", "/kmeans.R", "/deeplearning.R", "/randomforest.R", "/naivebayes.R", "/svd.R", "/locate.R", "/grid.R")
  require(jsonlite); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(ROOT.PATH, x, sep = ""))}))
}

#'
#' Source the tests/Utils/ directory.
#'
src.utils<-
function(ROOT.PATH) {
  to_src <- c("/h2oR.R", "/setupR.R", "/pcaR.R", "/deeplearningR.R", "/glmR.R", "/glrmR.R", "/gbmR.R", "/kmeansR.R", "/naivebayesR.R", "/utilsR.R", "/gridR.R")
  invisible(lapply(to_src,function(x){source(paste(ROOT.PATH, x, sep = ""))}))
}

#'
#' HDFS helpers
#'
H2O_INTERNAL_HDFS_NAME_NODE <- "172.16.2.176"

is.running.internal.to.h2o <- function() {
    url <- sprintf("http://%s:50070", H2O_INTERNAL_HDFS_NAME_NODE);
    internal <- url.exists(url, timeout = 5)
    return(internal)
}

# ----------------------------------------------------------------------

root.path  <- locate("h2o-package/R/", "h2o-r")
utils.path <- locate("tests/Utils/", "h2o-r")
src.utils(utils.path)
src(root.path)   # uncomment to source R code directly  (overrides package load)
library(h2o)
h2o.init(ip            = ipPort[[1]],
         port          = ipPort[[2]],
         startH2O      = FALSE)

#The master seed is set by the runnerSetup.R script.
#It serves as a way to reproduce all of the tests
master_seed_dir <- locate("tests", "h2o-r")
ms <- paste(master_seed_dir, .Platform$file.sep, "master_seed", sep = "")
seed <- NULL
if (file.exists(ms))  {
    MASTER_SEED <<- TRUE
    seed <- read.table(ms)[[1]]
}
setupRandomSeed(seed, suppress = FALSE)
sandbox()
h2o.logIt("[SEED] :", SEED, "Command")

h2o.removeAll()  # wipe at the beginning of every test

h2o.logAndEcho(new("H2OConnection", ip=myIP, port=myPort), "------------------------------------------------------------")
h2o.logAndEcho(new("H2OConnection", ip=myIP, port=myPort), "")
h2o.logAndEcho(new("H2OConnection", ip=myIP, port=myPort), paste("STARTING TEST: ", R.utils::commandArgs(asValues=TRUE)$"f"))
h2o.logAndEcho(new("H2OConnection", ip=myIP, port=myPort), "")
h2o.logAndEcho(new("H2OConnection", ip=myIP, port=myPort), "------------------------------------------------------------")

# Set up some directories.
if (exists("TEST_ROOT_DIR")) {
  H2O_JAR_DIR = sprintf("%s/../../target", TEST_ROOT_DIR)
}

# Clean up any temporary variables to avoid polluting the user's workspace.
options(echo=.origEchoValue)
options(scipen=999)
rm(list=c(".origEchoValue"))
