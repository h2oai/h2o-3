h2oTest.loadFeatureTestCase<-
function(testCaseId) {
    testCase <- new("FeatureTestCase")
    h2oTest.logInfo(slotNames(testCase))
    print(testCase@featureTestCasesCSV)
    featureTestCasesCSVPath <- h2oTest.locate(testCase@featureTestCasesCSV)
    print(featureTestCasesCSVPath)
    if (file.exists(featureTestCasesCSVPath)) {
        featureTestCasesCSVTable <- read.table(featureTestCasesCSVPath,header=TRUE)
    } else {
        h2oTest.fail(paste0("Couldn't find featureTestCases.csv which should be located in: ",featureTestCasesCSVPath))
    }
    print(featureTestCasesCSVTable)
}


h2oTest.executeFeatureTestCase<-
function(testCase) {
    print("I'm in the executeFeatureTestCase")
    print(testCase)

    if (testCase@feature == "cosine") {
        .executeUnMathOpFeatureTestCase(testCase, "cosine")
    }
    if (testCase@feature == "and") {
        .executeBinMathOpFeatureTestCase(testCase, "and")
    }
}

.executeUnMathOpFeatureTestCase<-
function(testCase, op) {
    dataSet <- .loadDataSets(testCase@dataSets,1)[[1]]

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined
    argsH2O <- .getUnMathOpArgsH2O(dataSet, h2oEnv)
    print("Finished .getArgsH2O")
    print(argsH2O)
    print("checking env")
    do.call("print",argsH2O,envir=h2oEnv)
    whatH2O <- .whatH2O(op)
    h2oRes <- do.call(what=whatH2O, args=argsH2O, envir=h2oEnv)
    print("checking h2o res")
    print(h2oRes)

    if (testCase@validationMethod == "R") {
        rEnv <- new.env()
        argsR <- .getUnMathOpArgsR(dataSet, rEnv)
        whatR <- .whatR(op)
        rRes  <- do.call(what=whatR, args=argsR, envir=rEnv)
        print("checking r res")
        print(rRes)
        .compareH2OToR(h2oRes, rRes)
    } else if (testCase@validationMethod == "H") {
        validationDataSet <- .loadDataSet(testCase@validationDataSetId)
        .compareH2OToHard(h2oRes, validationDataSet)
    }
}

.executeBinMathOpFeatureTestCase<-
function(testCase, op) {
    dataSets <- .loadDataSets(testCase@dataSets,2)
    left <- dataSets[[1]]
    right<- dataSets[[2]]

    print("Left and Right data sets")
    print(left)
    print(right)

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined
    argsH2O <- .getBinMathOpArgsH2O(left, right, h2oEnv)
    print("Finished .getBinMathOpArgsH2O")
    print(argsH2O)
    print("checking env")
    whatH2O <- .whatH2O(op)
    h2oRes <- do.call(what=whatH2O, args=argsH2O, envir=h2oEnv)
    print("checking h2o res")
    print(h2oRes)

    if (testCase@validationMethod == "R") {
        rEnv <- new.env()
        argsR <- .getBinMathOpArgsR(left, right, rEnv)
        whatR <- .whatR(op)
        rRes  <- do.call(what=whatR, args=argsR, envir=rEnv)
        print("checking r res")
        print(rRes)
        .compareH2OToR(h2oRes, rRes)
    } else if (testCase@validationMethod == "H") {
        validationDataSet <- .loadDataSet(testCase@validationDataSetId)
        .compareH2OToHard(h2oRes, validationDataSet)
    }
}

# modifies @key slot
.loadDataSets<-
function(dataSets, expected) {
    if (length(dataSets) != expected) {
        stop(paste0(".loadDataSets expected this test case's dataSets slot to be of length ", expected,", but got: ",length(dataSets)))
    }

    lapply(dataSets, function(d) {
        if (grepl("smalldata",d@uri)) {
            dataSetPath <- h2oTest.locate(paste0("smalldata",strsplit(d@uri,"smalldata")[[1]][2]))
        } else if (grepl("bigdata",dataSet@uri)) {
            dataSetPath <- h2oTest.locate(paste0("bigdata",strsplit(d@uri,"bigdata")[[1]][2]))
        }

        h2oDataSet <- h2o.importFile(dataSetPath)
        d@key <- attr(h2oDataSet,"id")
        d
    })
}

# modifies h2oEnv
.getUnMathOpArgsH2O<-
function(dataSet, h2oEnv) {
    fr <- h2o.getFrame(dataSet@key)
    print("In .getMathUnOpArgsH2O")
    print(fr)
    assign("fr", fr, envir=h2oEnv)
    return(list(as.name("fr")))
}

# modifies h2oEnv
.getBinMathOpArgsH2O<-
function(left, right, h2oEnv) {
    left <- h2o.getFrame(left@key)
    right <- h2o.getFrame(right@key)
    print("In .getBinMathOpArgsH2O")
    print(left)
    print(right)
    assign("left", left, envir=h2oEnv)
    assign("right", right, envir=h2oEnv)
    return(list(as.name("left"), as.name("right")))
}

# modifies rEnv
.getUnMathOpArgsR<-
function(dataSet, rEnv) {
    h2oFr <- h2o.getFrame(dataSet@key)
    fr <- as.data.frame(h2oFr)
    print("In .getMathUnOpArgsR")
    print(fr)
    assign("fr", fr, envir=rEnv)
    return(list(as.name("fr")))
}

# modifies rEnv
.getBinMathOpArgsR<-
function(left, right, rEnv) {
    leftH2O <- h2o.getFrame(left@key)
    left <- as.data.frame(leftH2O)
    rightH2O <- h2o.getFrame(right@key)
    right <- as.data.frame(rightH2O)
    print("In .getBinMathOpArgsR")
    print(left)
    print(right)
    assign("left", left, envir=rEnv)
    assign("right", right, envir=rEnv)
    return(list(as.name("left"), as.name("right")))
}


.whatH2O<-
function(op) {
    if (op == "cosine") { return("cos") }
    if (op == "and")    { return("&") }
}

.whatR<-
function(op) {
    if (op == "cosine") { return("cos") }
    if (op == "and")    { return("&") }
}

.compareH2OToR<-
function(h2oRes, rRes) {
    #dimensions
    nRowH <- nrow(h2oRes)
    nColH <- ncol(h2oRes)
    nRowR <- nrow(rRes)
    nColR <- ncol(rRes)

    if(nRowH != nRowR) {
        stop("Expected h2o's and R's results to have the same number of rows, but got: ", nRowH, " and ", nRowR, ", respectively.")
    }
    if(nColH != nColR) {
        stop("Expected h2o's and R's results to have the same number of cols, but got: ", nColH, " and ", nColR, ", respectively.")
    }
    #values
}

