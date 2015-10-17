#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)

#'#####################################################
#'
#'
#' h2o runit setup procedure and testing infrastructure
#'
#'
#'#####################################################

#'
#'
#' ----------------- Global variables -----------------
#'
#'
H2O.IP                      <<- "127.0.0.1"
H2O.PORT                    <<- 54321
ON.HADOOP                   <<- FALSE
HADOOP.NAMENODE             <<- NULL
SEED                        <<- NULL
PROJECT.ROOT                <<- "h2o-3"

#'
#'
#' ----------------- Main setup procedure -----------------
#'
#'
h2oRunitSetup <-
function() {
    testName <- R.utils::commandArgs(asValues=TRUE)$"f"

    # source h2o-r/h2o-package/R
    root.path <- locate("h2o-package/R/", "h2o-r")
    src(root.path)   # overrides package load

    # source h2o-r/tests/Utils
    utils.path <- locate("tests/Utils/", "h2o-r")
    src.utils(utils.path)

    parseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args

    default.packages()
    Log.info("Loaded default packages. Additional required packages must be loaded explicitly.\n")

    test.ip <- get.test.ip()
    test.port <- get.test.port()
    Log.info(paste0("Connect to h2o on IP: ",test.ip,", PORT: ",test.port,"\n"))
    h2o.init(ip = test.ip, port = test.port, startH2O = FALSE)

    setupRandomSeed()
    Log.info(paste0("[SEED] : ",get.test.seed()))

    sb <- sandbox(create=TRUE)
    Log.info(paste0("Created sandbox for test ",testName," in directory ",sb,".\n"))

    h2o.startLogging(paste(sb, "/rest.log", sep = ""))
    Log.info(paste0("Started rest logging in ",sb,"/rest.log.\n"))

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", testName))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")
}

#'
#'
#' ----------------- Testing infrastructure -----------------
#'
#'
get.test.ip       <- function() return(H2O.IP)
get.test.port     <- function() return(H2O.PORT)
hadoop.namenode   <- function() return(HADOOP.NAMENODE)
test.is.on.hadoop <- function() return(ON.HADOOP)
get.test.seed     <- function() return(SEED)
get.project.root  <- function() return(PROJECT.ROOT)

setupRandomSeed <- function() {
    maxInt <- .Machine$integer.max
    SEED <<- sample(maxInt, 1)
    set.seed(SEED)
}

#' Hadoop helpers
hadoop.namenode.is.accessible <- function() {
    url <- sprintf("http://%s:50070", hadoop.namenode());
    internal <- url.exists(url, timeout = 5)
    return(internal)
}

#' Locate a file given the pattern <bucket>/<path/to/file>
#' e.g. locate( "smalldata/iris/iris22.csv") returns the absolute path to iris22.csv
locate<-
function(pathStub, root.parent = NULL) {
  if (test.is.on.hadoop()) {
    # Jenkins jobs create symbolic links to smalldata and bigdata on the machine that starts the test. However,
    # in an h2o multinode hadoop cluster scenario, the clustered machines don't know about the symbolic link.
    # Consequently, `locate` needs to return the actual path to the data on the clustered machines. ALL jenkins
    # machines store smalldata and bigdata in /home/0xdiag/. If ON.HADOOP is set by the run.py, the pathStub arg MUST
    # be an immediate subdirectory of /home/0xdiag/. Moreover, the only guaranteed subdirectories of /home/0xdiag/ are
    # smalldata and bigdata.
    path <- normalizePath(paste0("/home/0xdiag/",pathStub))
    if (!file.exists(path)) stop(paste("Could not find the dataset: ", path, sep = ""))
    return(path)
  }
  pathStub <- clean(pathStub)
  bucket <- pathStub[1]
  offset <- pathStub[-1]
  cur.dir <- getwd()

  #recursively ascend until `bucket` is found
  bucket.abspath <- path.compute(cur.dir, bucket, root.parent)
  if (length(offset) != 0) return(paste(c(bucket.abspath, offset), collapse = "/", sep = "/"))
  else return(bucket.abspath)
}

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
    if (parent.name == get.project.root() || parent.name == "workspace") {
      if (root %in% dir(parent.dir)) return(normalizePath(paste(parent.dir, .Platform$file.sep, root, sep = "")))
      else stop(paste("Could not find the dataset bucket: ", root, sep = "" ))
    }
  # root.parent is not null
  } else {

    # first check if cur.dir is root
    if (basename(cur.dir) == root && parent.name == root.parent) return(normalizePath(cur.dir))

    # next check if root is in cur.dir somewhere (if so, then cur.dir is the parent!)
    if (root %in% dir(cur.dir) && root.parent == basename(cur.dir)) {
      return(normalizePath(paste(cur.dir, .Platform$file.sep, root, sep = ""))) }

    # the root is the parent
    if (parent.name == root && basename(dirname(parent.dir)) == root.parent) {
      return(path.compute(parent.dir, root, root.parent)) }

    # fail if reach h2o-dev
    if (parent.name == get.project.root()) {
        stop(paste0("Reached the root ", get.project.root(), ". Didn't find the bucket with the root.parent")) }
  }
  return(path.compute(parent.dir, root, root.parent))
}

#' Source the files in h2o-r/h2o-package/R/
src <-
function(ROOT.PATH) {
  to_src <- c("/classes.R", "/connection.R", "/constants.R", "/logging.R", "/communication.R", "/kvstore.R", "/frame.R",
              "/astfun.R", "/import.R", "/parse.R", "/export.R", "/models.R", "/edicts.R", "/gbm.R","/glm.R", "/glrm.R",
              "/kmeans.R", "/deeplearning.R", "/randomforest.R", "/naivebayes.R", "/pca.R", "/svd.R", "/locate.R",
              "/grid.R")
  invisible(lapply(to_src,function(x){source(paste(ROOT.PATH, x, sep = ""))}))
}

#' Source the utilities in h2o-r/tests/Utils/
src.utils<-
function(ROOT.PATH) {
  to_src <- c("/utilsR.R", "/pcaR.R", "/deeplearningR.R", "/glmR.R", "/glrmR.R", "/gbmR.R", "/kmeansR.R",
              "/naivebayesR.R", "/gridR.R", "/shared_javapredict.R")
  invisible(lapply(to_src,function(x){source(paste(ROOT.PATH, x, sep = ""))}))
}

#' Load a handful of packages automatically. Runit tests that require additional packages must be loaded explicitly
default.packages <-
function() {
  to_require <- c("jsonlite", "RCurl", "RUnit", "R.utils", "testthat", "ade4", "glmnet", "gbm", "ROCR", "e1071",
                  "tools", "statmod", "fpc", "cluster")
  if (Sys.info()['sysname'] == "Windows") {
    options(RCurlOptions = list(cainfo = system.file("CurlSSL", "cacert.pem", package = "RCurl"))) }
  invisible(lapply(to_require,function(x){require(x,character.only=TRUE,quietly=TRUE,warn.conflicts=FALSE)}))
}

h2oRunitSetup()

options(echo=.origEchoValue)
options(scipen=999)
rm(list=c(".origEchoValue"))
