#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)

#'#####################################################
#'
#'
#' feature test case execution procedure
#'
#'
#'#####################################################

#'
#' ------------- Argument parsing -------------
#'
parseFeatureTestCaseArgs<-
function(args) {
  featureTestCaseArgs <- list()
  i <- 1
  while (i <= length(args)) {
      s <- args[i]
      if (s == "--usecloud") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        argsplit <- strsplit(args[i], ":")[[1]]
        featureTestCaseArgs$ip <- argsplit[1]
        featureTestCaseArgs$port <- as.numeric(argsplit[2])
      } else if (s == "--hadoopNamenode") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        HADOOP.NAMENODE <<- args[i]
      } else if (s == "--onHadoop") {
        featureTestCaseArgs$onHadoop <- TRUE
      } else if (s == "--resultsDir") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$resultsDir <- as.character(args[i])
      } else if (s == "--testCaseId") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$testCaseId <- args[i]
      } else if (s == "--feature") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$feature <- args[i]
      } else if (s == "--featureParams") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$featureParams <- args[i]
      } else if (s == "--dataSetIds") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$dataSetIds <- args[i]
      } else if (s == "--validationMethod") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$validationMethod <- args[i]
      } else if (s == "--validationDataSetId") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$validationDataSetId <- args[i]
      } else if (s == "--featureTestCasesCSV") {
        i <- i + 1
        if (i > length(args)) featureTestCaseDriverUsage()
        featureTestCaseArgs$featureTestCasesCSV <- args[i]
      } else {
        unknownFeatureTestCaseArg(s)
      }
      i <- i + 1
  }
  return(featureTestCaseArgs)
}

featureTestCaseDriverUsage<-
function() {
  print("")
  print("Usage for:  R -f featureTestCaseDriver.R --args [...options...]")
  print("")
  print("    --usecloud             connect to h2o on specified ip and port, where ip and port are specified as follows:")
  print("                           IP:PORT")
  print("")
  print("    --onHadoop             Indication that tests will be run on h2o multinode hadoop clusters.")
  print("                           `locate` and `sandbox` runit test utilities use this indication in order to")
  print("                           behave properly. --hadoopNamenode must be specified if --onHadoop option is used.")
  print("    --hadoopNamenode       Specifies that the runit tests have access to this hadoop namenode.")
  print("                           `hadoop.namenode` runit test utility returns this value.")
  print("")
  print("    --resultsDir           the results directory.")
  print("")
  print("    --testId               the id of the test case.")
  print("")
  print("    --feature              the feature of the test case.")
  print("")
  print("    --featureParams        the parameters of the test case.")
  print("")
  print("    --dataSetIds           the id of the test case data sets.")
  print("")
  print("    --validationMethod     the validation method (R, H, O) of the test case.")
  print("")
  print("    --validationDataSetId  the id of the test case validation data sets (H option).")
  print("")
  print("    --featureTestCasesCSV  the path of the feature test cases csv file.")
  print("")
  q("no",1,FALSE) #exit with nonzero exit code
}

unknownFeatureTestCaseArg<-
function(arg) {
  print("")
  print(paste0("ERROR: Unknown argument: ",arg))
  print("")
  featureTestCaseDriverUsage()
}

#'
#' ----------------- h2o and h2o-test R package loading -----------------
#'
loadH2ORPackage <-
function(h2oRDir) {
    # load appropriate h2o-r/h2o-package/R/ functionality. overrides h2o package load
    to_src <- c("classes.R", "connection.R", "constants.R", "logging.R", "communication.R", "kvstore.R",
                "frame.R", "astfun.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R", "gbm.R",
                "glm.R", "glrm.R", "kmeans.R", "deeplearning.R", "randomforest.R", "naivebayes.R", "pca.R",
                "svd.R", "locate.R","grid.R")
    src_path <- paste(h2oRDir,"h2o-package","R",sep=.Platform$file.sep)
    invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))
}

loadH2ORTestPackage <-
function(h2oRDir) {
    # load appropriate h2o-r/h2o-test-package/R/ functionality. overrides h2o-test package load
    to_src <- c("utils.R", "feature.R", "classes.R")
    src_path <- paste(h2oRDir,"h2o-test-package","R",sep=.Platform$file.sep)
    invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))
}

#'
#' ----------------- helpers -----------------
#'
makeDataSets<-
function(dataSetIds, featureDataSetsCSV) {
    ids <- strsplit(dataSetIds,";")[[1]]
    featureDataSets <- read.csv(featureDataSetsCSV, header=TRUE)
    lapply(ids, function (id) {
        uri <- featureDataSets[featureDataSets$data_set_id == as.integer(id),2]
        FeatureDataSet(as.integer(id), as.character(uri))
    })
}

makeValidationDataSet<-
function(validationDataSetId, featureDataSetsCSV) {
    id <- as.integer(validationDataSetId)
    if (!is.na(id)) {
        featureDataSets <- read.csv(featureDataSetsCSV, header=TRUE)
        uri <- featureDataSets[featureDataSets$data_set_id == id, 2]
        return(FeatureDataSet(id, as.character(uri)))
    }
    return(NULL)
}

#'
#' ----------------- main -----------------
#'
featureTest <-
function(h2oTestFeatureDir) {

    h2oRDir <- normalizePath(paste(h2oTestFeatureDir,"..","h2o-r",sep=.Platform$file.sep))

    loadH2ORPackage(h2oRDir)

    loadH2ORTestPackage(h2oRDir)

    #featureTestCaseArgs <- defaultArgs() #set featureTestCasesCSV here too
    featureTestCaseArgs <- parseFeatureTestCaseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args
    print(featureTestCaseArgs)

    h2oTest.logInfo("Loading default R packages. Additional packages must be loaded explicitly.")
    h2oTest.loadDefaultRPackages()

    sb <- h2oTest.sandbox(create=TRUE, sandboxName=featureTestCaseArgs$testCaseId)
    h2oTest.logInfo(paste0("Created sandbox for feature test case ", featureTestCaseArgs$testCaseId,
                           " in directory ",sb,".\n"))

    seed <- h2oTest.setupSeed(sb)
    set.seed(seed)
    h2o.logIt("[SEED] :", seed)

    h2oTest.logInfo(paste0("Connecting to h2o on IP: ", featureTestCaseArgs$ip, ", PORT: ", featureTestCaseArgs$port))
    h2o.init(ip=featureTestCaseArgs$ip, port=featureTestCaseArgs$port, startH2O=FALSE, strict_version_check = FALSE)

    if (!is.null(featureTestCaseArgs$resultsDir)) {#you don't get the rest logs, unless you specify a results directory
        restLog <- paste(featureTestCaseArgs$resultsDir, "rest.log", sep = .Platform$file.sep)
        h2o.startLogging(restLog)
        h2oTest.logInfo(paste0("Started rest logging in: ", restLog))
    }

    # clean out h2o prior to running a feature test case
    h2o.removeAll()

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", featureTestCaseArgs$testCaseId))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    featureDataSetsCSV <- normalizePath(paste(h2oTestFeatureDir,"featureDataSets.csv", sep=.Platform$file.sep))
    print(featureDataSetsCSV)
    dataSets <- makeDataSets(featureTestCaseArgs$dataSetIds, featureDataSetsCSV)
    print(dataSets)
    validationDataSet <- makeValidationDataSet(featureTestCaseArgs$validationDataSetId, featureDataSetsCSV)
    print(validationDataSet)

    featureTestCase <- FeatureTestCase(
                       id = as.integer(featureTestCaseArgs$testCaseId),
                       feature = featureTestCaseArgs$feature,
                       featureParams = featureTestCaseArgs$featureParams,
                       dataSets = dataSets,
                       validationMethod = featureTestCaseArgs$validationMethod,
                       validationDataSet = validationDataSet)

    print(featureTestCase)

    h2oTest.executeFeatureTestCase(featureTestCase)
}

HADOOP.NAMENODE <<- NULL # TODO: Does this need to be global?
featureTest(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

options(echo=.origEchoValue)
