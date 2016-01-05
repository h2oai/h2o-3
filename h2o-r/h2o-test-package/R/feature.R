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
    } else if (testCase@feature == "colNames")  { op <- "colNames"
    }

    dataSets <- .loadDataSets(testCase@dataSets)

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined

    h2oArgSchema  <- .getArgSchema(op)
    print("The h2o parameter schema")
    print(h2oArgSchema)

    h2oFeatureParamsList <- .makeFeatureParamsList(testCase@featureParams, h2oArgSchema)
    print("The h2o feature params list")
    print(h2oFeatureParamsList)

    argsH2O <- .makeArgList(h2oArgSchema, h2oFeatureParamsList, dataSets, h2oEnv, FALSE)
    print("checking argsH2O")
    print(argsH2O)

    whatH2O <- .whatH2O(testCase@feature)

    h2oRes <- do.call(what=whatH2O, args=argsH2O, envir=h2oEnv)
    print("checking h2o res")
    print(h2oRes)

    if (testCase@validationMethod == "R") {
        rEnv <- new.env()

        rArgSchema  <- .getArgSchema(op)
        print("The R arg schema")
        print(rArgSchema)

        rFeatureParamsList <- .makeFeatureParamsList(testCase@featureParams, rArgSchema)
        print("The r feature params list")
        print(rFeatureParamsList)

        argsR <- .makeArgList(rArgSchema, rFeatureParamsList, dataSets, rEnv, TRUE)
        print("checking argsR")
        print(argsR)

        whatR <- .whatR(testCase@feature)

        rRes  <- do.call(what=whatR, args=argsR, envir=rEnv)
        print("checking r res")
        print(rRes)

        if (op == "unaryMath" || op == "binaryMath" || op == "cbind") {
            .compareH2OToRUnOrBinMathOp(h2oRes, rRes)
        } else if (op == "reducer") {
            .compareH2OToRRedOp(h2oRes, rRes)
        } else if (op == "colNames") {
            .compareH2OToRVector(h2oRes, rRes)
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
#' --------------- return the argument schema for the given operation ---------------
#'
.getArgSchema<-
function(op, r=FALSE) {
    if(op == "unaryMath" || op == "reducer" || op == "asFactor") {
        return(list(list(type="dataset", cardinality=1)))
    } else if (op == "binaryMath") {
        return(list(list(type="dataset", cardinality=2)))
    } else if (op == "cbind") {
        return(list(list(type="dataset", cardinality=-1)))
    } else if (op == "colNames") {
        return(list(list(type="dataset", cardinality=1),
                    list(type="logical", cardinality=1),
                    list(type="character", cardinality=1)))
    }
}


#'
#' --------------- parse the semi-colon-seperated feature parameters string into a list of params, which have been
#'                 casted to their correct type ---------------
#'
.makeFeatureParamsList<-
function(featureParamsString, pSchema) {
    # get all of the non dataset parameter schemas
    newPSchema <- list()
    c <- 1
    for(p in pSchema) {
        if (p$type != "dataset") {
            newPSchema[[c]] <- p
            c <- c + 1
        }
    }

    featureParamsList <- list()

    # make a list of all the parameters in their string format
    numArgs <- length(newPSchema) #TODO: this needs to take into account non dataset parameters with cardinality > 1
    if (numArgs == 0) { return(featureParamsList) }
    print(paste0("numArgs: ",numArgs))

    strList <- as.list(strsplit(featureParamsString,";")[[1]])
    print(paste0("strList: ",strList))

    numStrs <- length(strList)
    print(paste0("numStrs: ",numStrs))

    if (numStrs != 0) { for (i in 1:numStrs) { featureParamsList[[i]] <- strList[[i]] } }
    if (numArgs > numStrs) { featureParamsList[[numArgs]] <- "" }
    print(paste0("featureParamsList: ",featureParamsList))

    # cast the non-empty parameters to their proper type
    for (i in 1:numArgs) {
        if (featureParamsList[[i]] == "")      { next }
        if (newPSchema[[i]]$type == "logical") { featureParamsList[[i]] <- as.logical(featureParamsList[[i]]) }
        if (newPSchema[[i]]$type == "integer") { featureParamsList[[i]] <- as.integer(featureParamsList[[i]]) }
    }
    return(featureParamsList)
}


#'
#' --------------- get args for subsequent do.call ---------------
#'

# modifies env
.makeArgList<-
function(pSchema, featureParamsList, dataSets, env, r=FALSE) {
    parameterNameSpace <- LETTERS
    symbolCount <- 0
    dataSetCount <- 0
    featureParamCount <- 0

    argList <- list()
    for (p in pSchema) {
        if (p$type == "dataset") { # parameter is one or more data sets
            if (p$cardinality == -1) { # -1 means get all of the data sets in dataSets
                numDataSets <- length(dataSets)
            } else {
                numDataSets <- p$cardinality
            }
            for(i in 1:numDataSets) {
                fr <- h2o.getFrame(dataSets[[dataSetCount+1]]@key)
                if (r) { fr <- as.data.frame(fr) }
                assign(parameterNameSpace[symbolCount+1], fr, envir=env)
                dataSetCount <- dataSetCount + 1
                symbolCount <- symbolCount + 1
            }
        } else { # parameter is a non-dataset parameter
            assign(parameterNameSpace[symbolCount+1], featureParamsList[[featureParamCount+1]], envir=env)
            featureParamCount <- featureParamCount + 1
            symbolCount <- symbolCount + 1
        }
    }
    return(lapply(LETTERS[1:symbolCount], function(s) { as.name(s) }))
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

.compareH2OToRVector<-
function(h2oRes, rRes) {
    if (!all(h2oRes == rRes)) {
        stop("Expected h2o vector to be the same as R vector, but they're not.")
        print(paste0("h2o: ", h2oRes))
        print(paste0("r: ", rRes))
    }
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
    if (op == "colNames") { return("colnames") }
}

.whatR<-
function(op) {
    if (op == "cosine")   { return("cos") }
    if (op == "and")      { return("&") }
    if (op == "all")      { return("all") }
    if (op == "asFactor") { return("as.factor") }
    if (op == "cbind")    { return("cbind") }
    if (op == "colNames") { return("colnames") }
}