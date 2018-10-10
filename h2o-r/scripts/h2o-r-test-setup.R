#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)
options(scipen=999)

#'
#'
#' ----------------- R Test Global Variables -----------------
#'
#'
H2O.IP                      <<- "127.0.0.1"
H2O.PORT                    <<- 54321
ON.HADOOP                   <<- FALSE
HADOOP.NAMENODE             <<- Sys.getenv("NAME_NODE")
IS.RDEMO                    <<- FALSE
IS.RUNIT                    <<- FALSE
IS.RBOOKLET                 <<- FALSE
IS.RIPYNB                   <<- FALSE
RESULTS.DIR                 <<- NULL
REST.LOG                    <<- FALSE
TEST.NAME                   <<- "RTest"
SEED                        <<- NULL
PROJECT.ROOT                <<- "h2o-3"

#'
#'
#' ----------------- Arg Parsing -----------------
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
      } else if (s == "--rIPythonNotebook") {
        IS.RIPYNB <<- TRUE
      } else if (s == "--resultsDir") {
        i <- i + 1
        if (i > length(args)) usage()
        RESULTS.DIR <<- as.character(args[i])
      } else if (s == "--testName") {
        i <- i + 1
        if (i > length(args)) usage()
        TEST.NAME <<- args[i]
      } else if (s == '--restLog') {
        REST.LOG <<- TRUE
      }else {
        unknownArg(s)
      }
      i <- i + 1
  }
  if (sum(c(IS.RDEMO, IS.RUNIT, IS.RBOOKLET, IS.RIPYNB)) > 1) {
    print("Only one of the --rDemo, --rUnit, --rIPythonNotebook,  or --rBooklet options can be specified at a time.")
    usage()
  }
}

usage<-
function() {
  print("")
  print("Usage for:  R -f rtest.R --args [...options...]")
  print("")
  print("    --usecloud         connect to h2o on specified ip and port, where ip and port are specified as follows:")
  print("                       IP:PORT")
  print("")
  print("    --onHadoop         Indication that tests will be run on h2o multinode hadoop clusters.")
  print("                       `locate` and `sandbox` runit test utilities use this indication in order to")
  print("                       behave properly. --hadoopNamenode must be specified if --onHadoop option is used.")
  print("    --hadoopNamenode   Specifies that the runit tests have access to this hadoop namenode.")
  print("                       `hadoop.namenode` runit test utility returns this value.")
  print("")
  print("    --rDemo            test is R demo")
  print("")
  print("    --rUnit            test is R unit test")
  print("")
  print("    --rBooklet         test is R booklet")
  print("")
  print("    --rIPythonNotebook test is R IPython Notebook")
  print("")
  print("    --resultsDir       the results directory.")
  print("")
  print("    --testName         name of the rdemo, runit, or rbooklet.")
  print("")
  print("    --restLog          If set, enable REST API logging. Logs will be available at <resultsDir>/rest.log.")
  print("                       Please note, that enablig REST API logging will increase the execution time and that")
  print("                       the log file might be large (> 2GB).")
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

#'#####################################################
#'
#'
#' h2o r test (rdemo, runit, rbooklet) setup procedure
#'
#'
#'#####################################################

h2oTestSetup <-
function() {
    # find the h2o-r and h2o-docs directories
    thisFile <- sys.frame(1)$ofile
    if (!is.null(thisFile)) {
        h2oRDir <- normalizePath(paste(dirname(thisFile),"..",sep=.Platform$file.sep))
        h2oDocsDir <- normalizePath(paste(dirname(thisFile),"..","..","h2o-docs",sep=.Platform$file.sep))
    } else {
        h2oRDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..",sep=.Platform$file.sep))
        h2oDocsDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..","..","h2o-docs",
                                      sep=.Platform$file.sep))
    }

    parseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args

    if (all(!IS.RUNIT, !IS.RDEMO, !IS.RBOOKLET)) IS.RUNIT <<- TRUE  # default is runit test

    if (IS.RDEMO || IS.RBOOKLET || IS.RIPYNB) {
        if (!"h2o" %in% rownames(installed.packages())) {
            stop("The H2O package has not been installed on this system. Cannot execute the H2O R demo without it!") }
        require(h2o)
        if (IS.RDEMO || IS.RIPYNB) {
            # source h2o-r/demos/rdemoUtils
            invisible(source(paste(h2oRDir,"demos","rdemoUtils","utilsR.R",sep=.Platform$file.sep)))
            if (IS.RDEMO) TEST.NAME <<- removeH2OInit(TEST.NAME)
        } else {
            # source h2o-r/demos/rbookletUtils
            invisible(source(paste(h2oDocsDir,"src","booklets","v2_2015","source","rbookletUtils","utilsR.R",
            sep=.Platform$file.sep)))
        }
        strict_version_check <- TRUE
    } else if (IS.RUNIT) {
        # source h2o-r/h2o-package/R. overrides h2o package load
        to_src <- c("aggregator.R", "classes.R", "connection.R","config.R", "constants.R", "logging.R", "communication.R",
                    "kvstore.R", "frame.R", "astfun.R","automl.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R",
                    "coxph.R", "coxphutils.R", "gbm.R", "glm.R", "glrm.R", "kmeans.R", "deeplearning.R", "deepwater.R", "randomforest.R",
                    "naivebayes.R", "pca.R", "svd.R", "locate.R", "grid.R", "word2vec.R", "w2vutils.R", "stackedensemble.R",
                    "predict.R", "xgboost.R", "isolationforest.R")
        src_path <- paste(h2oRDir,"h2o-package","R",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        # source h2o-r/tests/runitUtils
        to_src <- c("utilsR.R", "pcaR.R", "deeplearningR.R", "glmR.R", "glrmR.R",
                    "gbmR.R", "kmeansR.R", "naivebayesR.R", "gridR.R", "shared_javapredict.R")
        src_path <- paste(h2oRDir,"tests","runitUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        default.packages()
        Log.info("Loaded default packages. Additional required packages must be loaded explicitly.")

        sb <- sandbox(create=TRUE)
        Log.info(paste0("Created sandbox for runit test ",TEST.NAME," in directory ",sb,".\n"))

        # setup the runit test seed
        seed <- NULL
        masterSeedFile <- paste(getwd(), "/master_seed", sep = "")
        if (file.exists(masterSeedFile)) seed <- read.table(masterSeedFile)[[1]]
        setupSeed(seed)
        h2o.logIt("[SEED] :", SEED)

        strict_version_check = FALSE
    } else {
        stop(paste0("Unrecognized test type. Must be of type rdemo, runit, or rbooklet, but got: ", TEST.NAME)) }

    cat(sprintf("[%s] %s\n", Sys.time(), paste0("Connect to h2o on IP: ",H2O.IP,", PORT: ",H2O.PORT)))
    h2o.init(ip = H2O.IP, port = H2O.PORT, startH2O = FALSE, strict_version_check = strict_version_check)

    if (!is.null(RESULTS.DIR) && REST.LOG) {
        h2o.startLogging(paste(RESULTS.DIR, "/rest.log", sep = ""))
        cat(sprintf("[%s] %s\n", Sys.time(),paste0("Started rest logging in: ",RESULTS.DIR,"/rest.log.")))
    }

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", TEST.NAME))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    # clean out h2o prior to running a test
    h2o.removeAll()

    # if rdemo or rbooklet, initiate the respective test here. if runit, run.py initiates test.
    if (IS.RDEMO || IS.RBOOKLET) {
      source(TEST.NAME)
    } else if (IS.RIPYNB) {
      ipyNotebookExec(TEST.NAME)
    }
}

h2oTestSetup()
options(echo=.origEchoValue)
