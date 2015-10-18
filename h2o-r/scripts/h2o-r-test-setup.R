#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)
options(scipen=999)

#'#####################################################
#'
#'
#' h2o r test (rdemo, runit, rbooklet) setup procedure
#'
#'
#'#####################################################

#'
#'
#' ----------------- Global variables and accessors -----------------
#'
#'
H2O.IP                      <<- "127.0.0.1"
H2O.PORT                    <<- 54321
ON.HADOOP                   <<- FALSE
HADOOP.NAMENODE             <<- NULL
IS.RDEMO                    <<- FALSE
IS.RUNIT                    <<- FALSE
IS.RBOOKLET                 <<- FALSE
RESULTS.DIR                 <<- NULL
TEST.NAME                   <<- NULL
SEED                        <<- sample(.Machine$integer.max, 1)
PROJECT.ROOT                <<- "h2o-3"

get.test.ip       <- function() return(H2O.IP)
get.test.port     <- function() return(H2O.PORT)
test.is.on.hadoop <- function() return(ON.HADOOP)
hadoop.namenode   <- function() return(HADOOP.NAMENODE)
test.is.rdemo     <- function() return(IS.RDEMO)
test.is.runit     <- function() return(IS.RUNIT)
test.is.rbooklet  <- function() return(IS.RBOOKLET)
results.dir       <- function() return(RESULTS.DIR)
test.name         <- function() return(TEST.NAME)
get.test.seed     <- function() return(SEED)
get.project.root  <- function() return(PROJECT.ROOT)

#'
#'
#' ----------------- Arg parsing -----------------
#'
#'
parseArgs<-
function(args) {
  i <- 1
  while (i <= length(args)) {
      s <- args[i]
      if (s == "--usecloud") {
        i <- i + 1
        if (i > length(args)) usage()
        argsplit <- strsplit(args[i], ":")[[1]]
        H2O.IP   <<- argsplit[1]
        H2O.PORT <<- as.numeric(argsplit[2])
      } else if (s == "--hadoopNamenode") {
        i <- i + 1
        if (i > length(args)) usage()
        HADOOP.NAMENODE <<- args[i]
      } else if (s == "--onHadoop") {
        ON.HADOOP <<- TRUE
      } else if (s == "--rDemo") {
        IS.RDEMO <<- TRUE
      } else if (s == "--rUnit") {
        IS.RUNIT <<- TRUE
      } else if (s == "--rBooklet") {
        IS.RBOOKLET <<- TRUE
      } else if (s == "--resultsDir") {
        i <- i + 1
        if (i > length(args)) usage()
        RESULTS.DIR <<- as.character(args[i])
      } else if (s == "--testName") {
        i <- i + 1
        if (i > length(args)) usage()
        TEST.NAME <<- args[i]
      } else {
        unknownArg(s)
      }
      i <- i + 1
  }
  if (sum(c(IS.RDEMO, IS.RUNIT, IS.RBOOKLET)) > 1) {
    print("Only one of the --rDemo, --rUnit, or --rBooklet options can be specified at a time.")
    usage()
  }
}

usage<-
function() {
  print("")
  print("Usage for:  R -f rtest.R --args [...options...]")
  print("")
  print("    --usecloud        connect to h2o on specified ip and port, where ip and port are specified as follows:")
  print("                      IP:PORT")
  print("")
  print("    --onHadoop        Indication that tests will be run on h2o multinode hadoop clusters.")
  print("                      `locate` and `sandbox` runit test utilities use this indication in order to")
  print("                      behave properly. --hadoopNamenode must be specified if --onHadoop option is used.")
  print("    --hadoopNamenode  Specifies that the runit tests have access to this hadoop namenode.")
  print("                      `hadoop.namenode` runit test utility returns this value.")
  print("")
  print("    --rDemo           test is R unit")
  print("")
  print("    --rUnit           test is R demo")
  print("")
  print("    --rBooklet        test is R booklet")
  print("")
  print("    --resultsDir      the results directory.")
  print("")
  print("    --testName        name of the rdemo, runit, or rbooklet.")
  print("")
  q("no",1,FALSE) #exit with nonzero exit code
}

unknownArg<-
function(arg) {
  print("")
  print(paste0("ERROR: Unknown argument: ",arg))
  print("")
  usage()
}

#'
#'
#' ----------------- Main setup procedure -----------------
#'
#'
h2oTestSetup <-
function() {
    h2oRDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..",sep=.Platform$file.sep))
    h2oDocsDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..","..","h2o-docs",
                                      sep=.Platform$file.sep))

    parseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args

    if (test.is.rdemo()) {
        if (!"h2o" %in% rownames(installed.packages())) {
            stop("The H2O package has not been installed on this system. Cannot execute the H2O R demo without it!")
        }
        require(h2o)

        # source h2o-r/demos/rdemoUtils
        to_src <- c("utilsR.R")
        src_path <- paste(h2oRDir,"demos","rdemoUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        TEST.NAME <<- removeH2OInit(test.name())
    } else if (test.is.runit()) {
        # source h2o-r/h2o-package/R. overrides h2o package load
        to_src <- c("classes.R", "connection.R", "constants.R", "logging.R", "communication.R", "kvstore.R",
                    "frame.R", "astfun.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R", "gbm.R",
                    "glm.R", "glrm.R", "kmeans.R", "deeplearning.R", "randomforest.R", "naivebayes.R", "pca.R",
                    "svd.R", "locate.R","grid.R")
        src_path <- paste(h2oRDir,"h2o-package","R",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        # source h2o-r/tests/runitUtils
        to_src <- c("utilsR.R", "pcaR.R", "deeplearningR.R", "glmR.R", "glrmR.R", "gbmR.R", "kmeansR.R",
                    "naivebayesR.R", "gridR.R", "shared_javapredict.R")
        src_path <- paste(h2oRDir,"tests","runitUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        default.packages()
        Log.info("Loaded default packages. Additional required packages must be loaded explicitly.")

        sb <- sandbox(create=TRUE)
        Log.info(paste0("Created sandbox for runit test ",test.name()," in directory ",sb,".\n"))
    } else if (test.is.rbooklet()) {
        if (!"h2o" %in% rownames(installed.packages())) {
            stop("The H2O package has not been installed on this system. Cannot execute the H2O R booklet without it!")
        }
        require(h2o)

        # source h2o-r/demos/rbookletUtils
        to_src <- c("utilsR.R")
        src_path <- paste(h2oDocsDir,"src","booklets","v2_2015","source","rbookletUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))
    } else {
        stop(paste0("Unrecognized test type. Must be of type rdemo, runit, or rbooklet, but got: ", test.name()))
    }

    test.ip <- get.test.ip()
    test.port <- get.test.port()
    cat(sprintf("[%s] %s\n", Sys.time(), paste0("Connect to h2o on IP: ",test.ip,", PORT: ",test.port)))
    h2o.init(ip = test.ip, port = test.port, startH2O = FALSE)

    set.seed(get.test.seed())
    cat(sprintf("[%s] %s\n", Sys.time(), paste0("[SEED] : ", get.test.seed())))

    h2o.startLogging(paste(results.dir(), "/rest.log", sep = ""))
    cat(sprintf("[%s] %s\n", Sys.time(),paste0("Started rest logging in: ",results.dir(),"/rest.log.")))

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", test.name()))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    # execute test
    if (test.is.rdemo()) {
        h2o.removeAll()
        conn <- h2o.getConnection()
        conn@mutable$session_id <- h2o:::.init.session_id()
    }
    source(test.name())
}

h2oTestSetup()
options(echo=.origEchoValue)
