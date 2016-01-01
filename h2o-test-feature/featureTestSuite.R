#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)
f <- R.utils::commandArgs(asValues=TRUE)$"f"

#'
#'
#' ----------------- Feature Test Case Driver State Variables -----------------
#'
#'

#TODO: do these need to be global?
H2O.IP                      <<- "127.0.0.1"
H2O.PORT                    <<- 54321
ON.HADOOP                   <<- FALSE
HADOOP.NAMENODE             <<- NULL
TEST.CASE.ID                <<- NULL
H2O.TEST.FEATURE.DIR        <<- normalizePath(dirname(f))
RESULTS.DIR                 <<- NULL # TODO: I think that the only reason we need this is for the rest logs. run.py is
# the only one who needs the results dir, unless an R -f user wants rest logs. who else is a user of this variable
H2O.R.DIR                   <<- normalizePath(paste(dirname(f),"..","h2o-r",sep=.Platform$file.sep))

#TODO: not sure we need these
SEED                        <<- NULL
PROJECT.ROOT                <<- "h2o-3"

#'#####################################################
#'
#'
#' feature test case execution procedure
#'
#'
#'#####################################################

#'
#' ----------------- helpers -----------------
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
      } else if (s == "--resultsDir") {
        i <- i + 1
        if (i > length(args)) usage()
        RESULTS.DIR <<- as.character(args[i])
      } else if (s == "--testCaseId") {
        i <- i + 1
        if (i > length(args)) usage()
        TEST.CASE.ID <<- args[i]
      } else {
        unknownArg(s)
      }
      i <- i + 1
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
  print("    --resultsDir      the results directory.")
  print("")
  print("    --testId          the id of the feature test.")
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

loadH2ORPackage <-
function() {
    # load appropriate h2o-r/h2o-package/R/ functionality. overrides h2o package load
    to_src <- c("classes.R", "connection.R", "constants.R", "logging.R", "communication.R", "kvstore.R",
                "frame.R", "astfun.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R", "gbm.R",
                "glm.R", "glrm.R", "kmeans.R", "deeplearning.R", "randomforest.R", "naivebayes.R", "pca.R",
                "svd.R", "locate.R","grid.R")
    src_path <- paste(H2O.R.DIR,"h2o-package","R",sep=.Platform$file.sep)
    invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))
}

loadH2ORTestPackage <-
function() {
    # load appropriate h2o-r/h2o-test-package/R/ functionality. overrides h2o-test package load
    to_src <- c("utils.R")
    src_path <- paste(H2O.R.DIR,"h2o-test-package","R",sep=.Platform$file.sep)
    invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))
}

#'
#' ----------------- main -----------------
#'
featureTest <-
function() {

    parseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args

    loadH2ORPackage()

    loadH2ORTestPackage()

    h2oTest.logInfo("Loading default R packages. Additional packages must be loaded explicitly.")
    h2oTest.loadDefaultRPackages()

    sb <- h2oTest.sandbox(create=TRUE)
    h2oTest.logInfo(paste0("Created sandbox for feature test case ", TEST.CASE.ID, " in directory ",sb,".\n"))

    # setup the seed for the test cases TODO: clean this up
    seed <- NULL
    masterSeedFile <- paste(getwd(), "/master_seed", sep = "")
    if (file.exists(masterSeedFile)) seed <- read.table(masterSeedFile)[[1]]
    h2oTest.setupSeed(seed)
    h2o.logIt("[SEED] :", SEED)

    h2oTest.logInfo(paste0("Connecting to h2o on IP: ", H2O.IP, ", PORT: ", H2O.PORT))
    h2o.init(ip = H2O.IP, port = H2O.PORT, startH2O = FALSE, strict_version_check = FALSE)

    if (!is.null(RESULTS.DIR)) { # you don't get the rest logs, unless you specify a results directory
        restLog <- paste(RESULTS.DIR, "rest.log", sep = .Platform$file.sep)
        h2o.startLogging(restLog)
        h2oTest.logInfo(paste0("Started rest logging in: ", restLog))
    }

    # clean out h2o prior to running a feature test case
    h2o.removeAll()

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", TEST.CASE.ID))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    featureTestCase <- h2oTest.loadFeatureTestCase(TEST.CASE.ID)
    h2oTest.executeFeatureTestCase(testCase)
}

featureTest()
options(echo=.origEchoValue)
