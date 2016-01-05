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


#'
#' --------------- execute a given test case ---------------
#'
h2oTest.executeFeatureTestCase<-
function(testCase) {
    print("I'm in the executeFeatureTestCase")
    print(testCase)

    op <- NULL
    if (testCase@feature == "cosine")           { op <- "unaryMath"
    } else if (testCase@feature == "all")       { op <- "reducer"
    } else if (testCase@feature == "and")       { op <- "binaryMath"
    } else if (testCase@feature == "asFactor")  { op <- "asFactor"
    } else if (testCase@feature == "cbind")     { op <- "cbind"
    }

    numDataSets <- length(testCase@dataSets)
    if (op == "unaryMath" || op == "reducer" || op == "asFactor") {
        if (numDataSets != 1) { stop(paste0("This test case requires 1 data set. Got: ", numDataSets)) }
        dataSet <- .loadDataSets(testCase@dataSets)[[1]]
    } else if (op == "binaryMath") {
        if (numDataSets != 2) { stop(paste0("This test case requires 2 data sets. Got: ", numDataSets)) }
        dataSets <- .loadDataSets(testCase@dataSets)
        left <- dataSets[[1]]
        right<- dataSets[[2]]
    } else if (op == "cbind") {
        if (numDataSets < 1) { stop(paste0("For `cbind`, must provide 1 or more data sets. Got: ", numDataSets)) }
        dataSets <- .loadDataSets(testCase@dataSets)
    }

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined

    if (op == "unaryMath" || op == "reducer" || op == "asFactor") {
        argsH2O <- .getH2OArgsUnMathOrRedOp(dataSet, h2oEnv)
    } else if (op == "binaryMath") {
        argsH2O <- .getH2OArgsBinMathOp(left, right, h2oEnv)
    } else if (op == "cbind") {
        argsH2O <- .getH2OArgsCbindOp(dataSets, h2oEnv)
    }

    print("checking argsH2O")
    print(argsH2O)

    whatH2O <- .whatH2O(testCase@feature)

    h2oRes <- do.call(what=whatH2O, args=argsH2O, envir=h2oEnv)
    print("checking h2o res")
    print(h2oRes)

    if (testCase@validationMethod == "R") {
        rEnv <- new.env()

        if (op == "unaryMath" || op == "reducer") {
            argsR <- .getRArgsUnMathOrReducerOp(dataSet, rEnv)
        } else if (op == "binaryMath") {
            argsR <- .getRArgsBinMathOp(left, right, rEnv)
        } else if (op == "cbind") {
            argsR <- .getRArgsCbindOp(dataSets, rEnv)
        }

        whatR <- .whatR(testCase@feature)

        rRes  <- do.call(what=whatR, args=argsR, envir=rEnv)
        print("checking r res")
        print(rRes)

        if (op == "unaryMath" || op == "binaryMath" || op == "cbind") {
            .compareH2OToRUnOrBinMathOp(h2oRes, rRes)
        } else if (op == "reducer") {
            .compareH2OToRRedOp(h2oRes, rRes)
        }
    } else if (testCase@validationMethod == "H") {
        validationDataSet <- .loadDataSets(testCase@dataSets,1)[[1]]
        print("checking validationDataSet")

        .compareH2OToHard(h2oRes, validationDataSet)
    } else if (testCase@validationMethod == "O") {
        if (op == "asFactor") { .asFactorValidation(h2oRes) }
    }
}

#'
#' --------------- load data sets into h2o ---------------
#'
# modifies @key slot
.loadDataSets<-
function(dataSets) {
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


#'
#' --------------- get args for subsequent do.call ---------------
#'
# modifies h2oEnv
.getH2OArgsUnMathOrRedOp<-
function(dataSet, h2oEnv) {
    fr <- h2o.getFrame(dataSet@key)
    print("In .getH2OArgsUnMathOrRedOp")
    print(fr)
    assign("fr", fr, envir=h2oEnv)
    return(list(as.name("fr")))
}

# modifies h2oEnv
.getH2OArgsBinMathOp<-
function(left, right, h2oEnv) {
    left <- h2o.getFrame(left@key)
    right <- h2o.getFrame(right@key)
    print("In .getH2OArgsBinMathOp")
    print(left)
    print(right)
    assign("left", left, envir=h2oEnv)
    assign("right", right, envir=h2oEnv)
    return(list(as.name("left"), as.name("right")))
}

# modifies h2oEnv
.getH2OArgsCbindOp<-
function(dataSets, h2oEnv) {
    numDataSets <- length(dataSets)
    symbols <- LETTERS[1:numDataSets]
    for(i in 1:numDataSets) { assign(symbols[i], h2o.getFrame(dataSets[[i]]@key), envir=h2oEnv) }
    lapply(symbols, function (s) { as.name(s) })
}

# modifies rEnv
.getRArgsUnMathOrReducerOp<-
function(dataSet, rEnv) {
    h2oFr <- h2o.getFrame(dataSet@key)
    fr <- as.data.frame(h2oFr)
    print("In .getRArgsUnMathOrReducerOp")
    print(fr)
    assign("fr", fr, envir=rEnv)
    return(list(as.name("fr")))
}

# modifies rEnv
.getRArgsBinMathOp<-
function(left, right, rEnv) {
    leftH2O <- h2o.getFrame(left@key)
    left <- as.data.frame(leftH2O)
    rightH2O <- h2o.getFrame(right@key)
    right <- as.data.frame(rightH2O)
    print("In .getRArgsBinMathOp")
    print(left)
    print(right)
    assign("left", left, envir=rEnv)
    assign("right", right, envir=rEnv)
    return(list(as.name("left"), as.name("right")))
}

# modifies rEnv
.getRArgsCbindOp<-
function(dataSets, rEnv) {
    numDataSets <- length(dataSets)
    symbols <- LETTERS[1:numDataSets]
    for(i in 1:numDataSets) { assign(symbols[i], as.data.frame(h2o.getFrame(dataSets[[i]]@key)), envir=rEnv) }
    lapply(symbols, function (s) { as.name(s) })
}


#'
#' --------------- get what for subsequent do.call ---------------
#'
.whatH2O<-
function(op) {
    if (op == "cosine")   { return("cos") }
    if (op == "and")      { return("&") }
    if (op == "all")      { return("all") }
    if (op == "asFactor") { return("as.factor") }
    if (op == "cbind")    { return("h2o.cbind") }
}

.whatR<-
function(op) {
    if (op == "cosine")   { return("cos") }
    if (op == "and")      { return("&") }
    if (op == "all")      { return("all") }
    if (op == "asFactor") { return("as.factor") }
    if (op == "cbind")    { return("cbind") }
}


#'
#' --------------- validation methods ---------------
#'
.compareH2OToRUnOrBinMathOp<-
function(h2oRes, rRes) {
    #dimensions
    nRowH <- nrow(h2oRes)
    nColH <- ncol(h2oRes)
    nRowR <- nrow(rRes)
    nColR <- ncol(rRes)

    if(nRowH != nRowR) {
        stop(paste0("Expected h2o's and R's results to have the same number of rows, but got: ", nRowH, " and ", nRowR, ", respectively."))
    }
    if(nColH != nColR) {
        stop(paste0("Expected h2o's and R's results to have the same number of cols, but got: ", nColH, " and ", nColR, ", respectively."))
    }
    #values
}

.compareH2OToRRedOp<-
function(h2oRes, rRes) {
    if (h2oRes != rRes) {
        stop(paste0("Expected h2o's and R's results to be the same, but got: ", h2oRes, " and ", rRes, ", respectively."))
    }
}

.asFactorValidation<-
function(h2oRes) {
    if (!is.factor(h2oRes[,1])) {
        stop("Expected column 1 of h2o frame to be a factor, but it's not.")
    }
}

