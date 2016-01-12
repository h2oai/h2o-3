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
#' ------------- Command Arguments Parsing -------------
#'
parseCommandArgs<-
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
  h2oTest.logInfo("")
  h2oTest.logInfo("Usage for:  R -f featureTestCaseDriver.R --args [...options...]")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --usecloud IP:PORT            (optional) connect to h2o on the specified IP and PORT.")
  h2oTest.logInfo("                                  If unspecified, then localhost:54321 is used.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --onHadoop                    (optional) Indication that tests will be run on h2o multinode hadoop")
  h2oTest.logInfo("                                  clusters. `locate` and `sandbox` runit test utilities use this indication ")
  h2oTest.logInfo("                                  in order to behave properly. --hadoopNamenode must be specified if ")
  h2oTest.logInfo("                                  --onHadoop option is used.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --hadoopNamenode NN           (optional) Specifies that the feature tests have access to hadoop namenode,")
  h2oTest.logInfo("                                  NN, where NN is a valid hadoop namenode.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --resultsDir DIR              (optional) if specified, then `h2o.startLogging` places the REST logs in ")
  h2oTest.logInfo("                                  this directory. if unspecified, then REST logging with not occur.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --testCaseId ID               (required) id of the test case.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --feature F                   (optional) feature of the test case. If unspecified, then an attempt is")
  h2oTest.logInfo("                                  made to retrieve this value from the test case csv file.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --featureParams FP            (optional) parameters of the test case. If unspecified, then an attempt")
  h2oTest.logInfo("                                  is made to retrieve this value from the test case csv file.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --dataSetIds IDS              (optional) data set ids of the test case. If unspecified, then an ")
  h2oTest.logInfo("                                  attempt is made to retrieve this value from the test case csv file.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --validationMethod M          (optional) validation method (R, H, O) of the test case. If unspecified,")
  h2oTest.logInfo("                                  then an attempt is made to retrieve this value from the test case csv file.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --validationDataSetId ID      (optional) id of the test case validation data set. If unspecified, ")
  h2oTest.logInfo("                                  then an attempt is made to retrieve this value from the test case csv file.")
  h2oTest.logInfo("")
  h2oTest.logInfo("    --featureTestCasesCSV PATH    (optional) path of the feature test cases csv file. If unspecified, then")
  h2oTest.logInfo("                                  h2o-3/h2o-test-feature/featureTestCases.csv is used.")
  h2oTest.logInfo("")
  q("no",1,FALSE) #exit with nonzero exit code
}

unknownFeatureTestCaseArg<-
function(arg) {
  h2oTest.logInfo("")
  h2oTest.logInfo(paste0("ERROR: Unknown argument: ",arg))
  h2oTest.logInfo("")
  featureTestCaseDriverUsage()
}

getDriverArgs<-
function(cArgs) {
    dArgs <- parseCommandArgs(cArgs) # provided by --args
    if (is.null(dArgs$testCaseId))   { featureTestCaseDriverUsage(); h2oTest.fail("Must specify --testCaseId!"); }

    # defaults, if missing
    if (is.null(dArgs[['ip']]))   { dArgs$ip   <- "localhost" }
    if (is.null(dArgs[['port']])) { dArgs$port <- 54321 }
    if (is.null(dArgs[['featureTestCasesCSV']])) {
        dArgs$featureTestCasesCSV <- h2oTest.locate("h2o-test-feature/featureTestCases.csv")
    }
    if (is.null(dArgs$feature) || is.null(dArgs$featureParams) || is.null(dArgs$dataSetIds) ||
        is.null(dArgs$validationMethod) || is.null(dArgs$validationDataSetId)) {
        if (file.exists(dArgs$featureTestCasesCSV)) {
            testCases <- read.table(dArgs$featureTestCasesCSV,header=TRUE,sep="|")
        } else {
            h2oTest.fail(paste0("Couldn't find featureTestCases.csv which should be located in: ",
                                dArgs$featureTestCasesCSV))
        }

        testCase <- testCases[testCases$id == dArgs$testCaseId,]
        if (nrow(testCase) == 0) { h2oTest.fail(paste0("Couldn't find test case id ", dArgs$testCaseId, " in csv.")) }

        if (is.null(dArgs[['feature']]))                { dArgs$feature <- as.character(testCase$feature) }
        if (is.null(dArgs[['featureParams']]))          { dArgs$featureParams <- as.character(testCase$feature_params) }
        if (is.null(dArgs[['dataSetIds']]))             { dArgs$dataSetIds <- as.character(testCase$data_set_ids) }
        if (is.null(dArgs[['validationMethod']]))       {
            dArgs$validationMethod <- as.character(testCase$validation_method)
        }
        if (is.null(dArgs[['validationDataSetId']]))    {
            if (is.na(testCase$validation_data_set_id)) { dArgs$validationDataSetId <- ""
            } else                                      {
                dArgs$validationDataSetId <- as.character(testCase$validation_data_set_id)
            }
        }
    }
    return(dArgs)
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
#' ----------------- Helpers -----------------
#'
makeTestCase<-
function(driverArgs, featureDataSetsCSV) {
    ids <- strsplit(driverArgs$dataSetIds,";")[[1]]
    dataSets <- lapply(ids, function (id) { makeDataSet(as.integer(id), featureDataSetsCSV) })
    validationDataSet <- makeDataSet(as.integer(driverArgs$validationDataSetId), featureDataSetsCSV)
    featureTestCase <- FeatureTestCase(
                       id = as.integer(driverArgs$testCaseId),
                       feature = driverArgs$feature,
                       featureParams = driverArgs$featureParams,
                       dataSets = dataSets,
                       validationMethod = driverArgs$validationMethod,
                       validationDataSet = validationDataSet)
    return(featureTestCase)
}

makeDataSet<-
function(id, featureDataSetsCSV) {
    if (!is.na(id)) {
        featureDataSets <- read.csv(featureDataSetsCSV, header=TRUE)
        uri <- featureDataSets[featureDataSets$data_set_id == id, 2]
        return(FeatureDataSet(id, as.character(uri)))
    }
    return(NULL)
}


#'
#' ----------------- Main -----------------
#'
featureTest <-
function() {
    driver <- R.utils::commandArgs(asValues=TRUE)$"f"
    h2oTestFeatureDir <- normalizePath(dirname(driver))
    h2oRDir <- normalizePath(paste(h2oTestFeatureDir,"..","h2o-r",sep=.Platform$file.sep))
    featureDataSetsCSV <- normalizePath(paste(h2oTestFeatureDir,"featureDataSets.csv", sep=.Platform$file.sep))

    loadH2ORPackage(h2oRDir)
    loadH2ORTestPackage(h2oRDir)
    
    h2oTest.logInfo("Loading default R packages. Additional packages must be loaded explicitly.")
    h2oTest.loadDefaultRPackages()

    driverArgs <- getDriverArgs(commandArgs(trailingOnly=TRUE))
    h2oTest.logInfo("Feature Test Case Driver Arguments: ")
    print(driverArgs)

    sb <- h2oTest.sandbox(create=TRUE, sandboxName=driverArgs$testCaseId)
    h2oTest.logInfo(paste0("Created sandbox for feature test case ", driverArgs$testCaseId,
                           " in directory ",sb,".\n"))

    seed <- h2oTest.setupSeed(sb)
    set.seed(seed)
    h2o.logIt("[SEED] :", seed)

    h2oTest.logInfo(paste0("Connecting to h2o on IP: ", driverArgs$ip, ", PORT: ", driverArgs$port))
    h2o.init(ip=driverArgs$ip, port=driverArgs$port, startH2O=FALSE, strict_version_check = FALSE)

    if (!is.null(driverArgs$resultsDir)) { # you don't get the rest logs, unless you specify a results directory
        restLog <- paste(driverArgs$resultsDir, "rest.log", sep = .Platform$file.sep)
        h2o.startLogging(restLog)
        h2oTest.logInfo(paste0("Started rest logging in: ", restLog))
    }

    h2o.removeAll()
    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", driverArgs$testCaseId))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    testCase <- makeTestCase(driverArgs, featureDataSetsCSV)
    h2oTest.logInfo("Executing Test Case: ")
    print(testCase)
    h2oTest.executeFeatureTestCase(testCase)
}

HADOOP.NAMENODE <<- NULL # TODO: Does this need to be global?
featureTest()

options(echo=.origEchoValue)

