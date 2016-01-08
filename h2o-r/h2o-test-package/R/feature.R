#'
#' --------------- execute a given test case ---------------
#'
h2oTest.executeFeatureTestCase<-
function(testCase) {

    dataSets <- .loadDataSets(testCase@dataSets)

    h2oFeatureParamsList <- .makeH2OFeatureParamsList(testCase@featureParams)
    print("The h2o feature params list")
    print(h2oFeatureParamsList)

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined
    argsH2O <- .makeArgList(h2oFeatureParamsList, dataSets, testCase@feature, h2oEnv, FALSE)
    print("checking argsH2O")
    print(argsH2O)

    h2oRes <- do.call(what=testCase@feature, args=argsH2O, envir=h2oEnv)
    print("checking h2o res")
    print(h2oRes)

    h2oReturnClass <- class(h2oRes)
    print("class of h2o res")
    print(h2oReturnClass)

    if (testCase@validationMethod == "R") {

        rFeatureParamsList <- .translateH2OFeatureParamsListToR(h2oFeatureParamsList, testCase@feature)
        print("The r feature params list")
        print(rFeatureParamsList)

        rEnv <- new.env()
        argsR <- .makeArgList(rFeatureParamsList, dataSets, testCase@feature, rEnv, TRUE)
        print("checking argsR")
        print(argsR)

        rRes  <- do.call(what=.whatR(testCase@feature), args=argsR, envir=rEnv)
        print("checking r res")
        print(rRes)

        rReturnClass <- class(rRes)
        print("class of r res")
        print(rReturnClass)

        .validateRAndH2OResultComparability(h2oReturnClass, rReturnClass)

        .compareH2OToR(h2oRes, rRes, h2oReturnClass, rReturnClass)

    } else if (testCase@validationMethod == "H") { #TODO: allow hard-coded frames, integers, expressions ... anything that h2o can return
        validationDataSet <- .loadDataSets(list(testCase@validationDataSet))[[1]]
        print("checking validationDataSet")
        print(validationDataSet)
        vFr <- h2o.getFrame(validationDataSet@key)
        print(vFr)
        .compare2Frames(h2oRes, vFr)
    } else if (testCase@validationMethod == "O") {
        if (testCase@feature == "asFactor") { .asFactorValidation(h2oRes) }
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
#' --------------- parse the semi-colon-seperated feature parameters string into a list of params ---------------
#'
# TODO: doesn't allow empty parameters in parameter string. all parameters need to be specified (in order).
# allow optional parameters.
.makeH2OFeatureParamsList<-
function(featureParamsString) {
    featureParamsList <- as.list(strsplit(featureParamsString,";")[[1]])
    print("strList: ")
    print(featureParamsList)

    if (length(featureParamsList) == 0) { return(featureParamsList) }
    # evaluate the individual parameter strings
    for (i in 1:length(featureParamsList)) {
        p <- eval(parse(text=featureParamsList[[i]])) # parameters in the the parameter string should be valid R expressions
        if (is.null(p)) { featureParamsList[i]   <- list(NULL)
        } else {          featureParamsList[[i]] <- p }
    }
    return(featureParamsList)
}


#'
#' --------------- translate the list of h2o parameters into the proper R parameters ---------------
#'
.translateH2OFeatureParamsListToR<-
function(h2oFeatureParamsList, feature) {
    if (feature == "h2o.quantile") { return(h2oFeatureParamsList[1])
    } else                         { return(h2oFeatureParamsList)
    }
}


#'
#' --------------- return the data sets type for the given h2o or r feature ---------------
#'
.getRDataSetsType<-
function(feature) {
    if (feature %in% c("as.factor", "cos", "all", "&", "h2o.cbind", "h2o.table", "colnames", "[", "h2o.hist",
                       "h2o.impute")) {
        return("data.frame")
    } else if (feature %in% c("h2o.quantile", "cut", "h2o.match")) {
        return("numeric")
    } else if (feature %in% c("h2o.which")) {
        return("logical")
    }
}

#'
#' --------------- simple protocol. put the data sets (in the order given) on the arg list first. next, put the
#' --------------- parameters (in the order given) on the arg list.
#'
# modifies env
.makeArgList<-
function(featureParamsList, dataSets, feature, env, r) {
    parameterNameSpace <- LETTERS
    symbolCount <- 0

    if (r) { dataSetsType <- .getRDataSetsType(feature)
    } else { dataSetsType <- "H2OFrame" }

    for (d in dataSets) {
        fr <- h2o.getFrame(d@key)
        if        (dataSetsType == "H2OFrame")   { dArg <- fr
        } else if (dataSetsType == "data.frame") { dArg <- as.data.frame(fr)
        } else if (dataSetsType == "numeric")    { dArg <- as.numeric(as.data.frame(fr[,1])[,1]) # we only take the first column of the dataset source file
        } else if (dataSetsType == "logical")    { dArg <- as.logical(as.data.frame(fr[,1])[,1])
        }
        assign(parameterNameSpace[symbolCount+1], dArg, envir=env)
        symbolCount <- symbolCount + 1
    }

    for (f in featureParamsList) {
        assign(parameterNameSpace[symbolCount+1], f, envir=env)
        symbolCount <- symbolCount + 1
    }
    return(lapply(LETTERS[1:symbolCount], function(s) { as.name(s) }))
}


#'
#' --------------- ensure the h2o and r results are comparable ---------------
#'
.validateRAndH2OResultComparability<-
function(h2oReturnClass, rReturnClass) {
    if        (h2oReturnClass == "H2OFrame"  && rReturnClass == "data.frame") {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "matrix")     {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "table")      {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "factor")     {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "numeric")    {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "character")  {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "integer")    {
    } else if (h2oReturnClass == "factor"    && rReturnClass == "factor")     {
    } else if (h2oReturnClass == "numeric"   && (rReturnClass %in% c("numeric", "integer", "double")))   {
    } else if (h2oReturnClass == "character" && rReturnClass == "character")  {
    } else if (h2oReturnClass == "logical"   && rReturnClass == "logical")    {
    } else {
        stop("H2O's and R's results cannot be compared because they are not compatible classes.")
    }
}


#'
#' --------------- validation methods ---------------
#'
.compareH2OToR<-
function(h2oRes, rRes, h2oReturnClass, rReturnClass) {
    if        (h2oReturnClass == "H2OFrame"  && rReturnClass == "data.frame") { .compare2Frames(h2oRes, rRes)
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "matrix")     { .compareFrameAndMatrix(h2oRes, rRes)
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "table")      { .compareFrameAndTable(h2oRes, rRes)
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "factor")     { .compareFrameAndBase(h2oRes, rRes)
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "integer")    { .compareFrameAndBase(h2oRes, rRes)
    } else if (h2oReturnClass == "factor"    && rReturnClass == "factor")     { .compare2Bases(h2oRes, rRes)
    } else if (h2oReturnClass == "numeric"   && is(rReturnClass,"numeric"))   { .compare2Bases(h2oRes, rRes)
    } else if (h2oReturnClass == "character" && rReturnClass == "character")  { .compare2Bases(h2oRes, rRes)
    } else if (h2oReturnClass == "logical"   && rReturnClass == "logical")    { .compare2Bases(h2oRes, rRes)
    }
}

.compare2Frames<-
function(h2oRes, rRes) {
    #dimensions
    h2oRes <- as.data.frame(h2oRes)
    rRes   <- as.data.frame(rRes)
    nRowH <- nrow(h2oRes)
    nColH <- ncol(h2oRes)
    nRowR <- nrow(rRes)
    nColR <- ncol(rRes)

    if(nRowH != nRowR) {
        stop(paste0("Expected h2o's and R's results to have the same number of rows, but got: ", nRowH, " and ",
                    nRowR, ", respectively."))
    }
    if(nColH != nColR) {
        stop(paste0("Expected h2o's and R's results to have the same number of cols, but got: ", nColH, " and ",
                    nColR, ", respectively."))
    }
    #values
}

.compareFrameAndMatrix<-
function(h2oRes, rRes) {
    .compare2Frames(h2oRes, as.data.frame(rRes))
}

.compareFrameAndTable<-
function(h2oRes, rRes) {
    if (ncol(h2oRes) > 2) { # case 2 dimensions
        .compare2Frames(h2oRes[,2:ncol(h2oRes)], as.data.frame.matrix(rRes))
    } else { # case 1 dimension
        .compare2Frames(h2oRes[,2],as.data.frame(as.data.frame(rRes)[,2]))
    }
}

.compareFrameAndBase<-
function(h2oRes, rRes) {
    numH2OCols <- ncol(h2oRes)
    if (!(numH2OCols == 1)) {
        stop(paste0("Expected the H2OFrame to have 1 column, but got: ", numH2OCols))
    }
    numH2OEntries <- nrow(h2oRes)
    numREntries   <- length(rRes)
    if (!(numH2OEntries == numREntries)) {
        stop(paste0("Expected the same number of h2o entries as R entries, but got: ", numH2OEntries, " and ",
                    numREntries, ", respectively."))
    }
    .compare2Frames(h2oRes, as.h2o(rRes))
}

.compare2Bases<-
function(h2oRes, rRes) {
    if (!all(h2oRes == rRes)) {
        stop(paste0("Expected h2o's and R's results to be the same, but got: ", h2oRes, " and ", rRes,
                    ", respectively."))
    }
}

.asFactorValidation<-
function(h2oRes) {
    if (!is.factor(h2oRes[,1])) {
        stop("Expected column 1 of h2o frame to be a factor, but it's not.")
    }
}


#'
#' --------------- get what for subsequent do.call ---------------
#'
.whatR<-
function(op) {
    if (op == "cos")                  { return("cos")
    } else if (op == "&")             { return("&")
    } else if (op == "all")           { return("all")
    } else if (op == "as.factor")     { return("as.factor")
    } else if (op == "h2o.cbind")     { return("cbind")
    } else if (op == "colnames")      { return("colnames")
    } else if (op == "[")             { return("[")
    } else if (op == "h2o.table")     { return("table")
    } else if (op == "h2o.quantile")  { return("quantile")
    } else if (op == "cut")           { return("cut")
    } else if (op == "h2o.match")     { return("match")
    } else if (op == "h2o.which")     { return("which")
    }
}


#h2oTest.loadFeatureTestCase<-
#function(testCaseId) {
#    testCase <- new("FeatureTestCase")
#    h2oTest.logInfo(slotNames(testCase))
#    print(testCase@featureTestCasesCSV)
#    featureTestCasesCSVPath <- h2oTest.locate(testCase@featureTestCasesCSV)
#    print(featureTestCasesCSVPath)
#    if (file.exists(featureTestCasesCSVPath)) {
#        featureTestCasesCSVTable <- read.table(featureTestCasesCSVPath,header=TRUE,sep="|")
#    } else {
#        h2oTest.fail(paste0("Couldn't find featureTestCases.csv which should be located in: ",featureTestCasesCSVPath))
#    }
#    print(featureTestCasesCSVTable)
#}