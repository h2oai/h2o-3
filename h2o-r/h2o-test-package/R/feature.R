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


#'
#' --------------- execute a given test case ---------------
#'
h2oTest.executeFeatureTestCase<-
function(testCase) {

    dataSets <- .loadDataSets(testCase@dataSets)

    h2oArgSchemaType <- .getArgSchemaType(testCase@feature,FALSE)
    print("The h2o arg schema type")
    print(h2oArgSchemaType)

    h2oArgSchema  <- .getArgSchema(h2oArgSchemaType)
    print("The h2o parameter schema")
    print(h2oArgSchema)

    h2oFeatureParamsList <- .makeFeatureParamsList(testCase@featureParams, h2oArgSchema)
    print("The h2o feature params list")
    print(h2oFeatureParamsList)

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined
    argsH2O <- .makeArgList(h2oArgSchema, h2oFeatureParamsList, dataSets, h2oEnv, FALSE)
    print("checking argsH2O")
    print(argsH2O)

    whatH2O <- .whatH2O(testCase@feature)

    h2oRes <- do.call(what=whatH2O, args=argsH2O, envir=h2oEnv)
    print("checking h2o res")
    print(h2oRes)

    h2oReturnClass <- .validateH2OReturnClass(h2oRes, testCase@feature)
    print("class of h2o res")
    print(h2oReturnClass)

    if (testCase@validationMethod == "R") {

        rArgSchemaType <- .getArgSchemaType(testCase@feature,TRUE)
        print("The R arg schema type")
        print(rArgSchemaType)

        rArgSchema  <- .getArgSchema(rArgSchemaType)
        print("The R arg schema")
        print(rArgSchema)

        rFeatureParamsList <- .makeFeatureParamsList(testCase@featureParams, rArgSchema)
        print("The r feature params list")
        print(rFeatureParamsList)

        rEnv <- new.env()
        argsR <- .makeArgList(rArgSchema, rFeatureParamsList, dataSets, rEnv, TRUE)
        print("checking argsR")
        print(argsR)

        whatR <- .whatR(testCase@feature)

        rRes  <- do.call(what=whatR, args=argsR, envir=rEnv)
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
#' --------------- return the argument schema type for the given feature ---------------
#'
.getArgSchemaType<-
function(feature, r) {
    if (feature %in% c("asFactor", "cosine", "all")) {
        return("df1")
    } else if (feature %in% c("and")) {
        return("df2")
    } else if (feature %in% c("cbind", "table")) {
        return("df-1")
    } else if (feature %in% c("colNames")) {
        return("df1l1c1")
    } else if (feature %in% c("slice")) {
        return("df1i1i1")
    } else if (feature %in% c("histogram")) {
        return("df1c1")
    } else if (feature %in% c("impute")) {
        return("df1i1c1c1i1l1")
    } else if (feature %in% c("quantile")) {
        if (!r) {
            return("df1n1c1n1")
        } else {
            return("dn1n1")
        }
    }
}


#'
#' --------------- return the argument schema for the given schema type ---------------
#'
.getArgSchema<-
function(schemaType) {
    if        (schemaType == "df1")           { return(list(list(type="dataset",   cardinality=1,  dtype="frame")))
    } else if (schemaType == "df2")           { return(list(list(type="dataset",   cardinality=2,  dtype="frame")))
    } else if (schemaType == "df-1")          { return(list(list(type="dataset",   cardinality=-1, dtype="frame")))
    } else if (schemaType == "df1l1c1")       { return(list(list(type="dataset",   cardinality=1,  dtype="frame"),
                                                            list(type="logical",   cardinality=1),
                                                            list(type="character", cardinality=1)))
    } else if (schemaType == "df1i1i1")       { return(list(list(type="dataset",   cardinality=1,  dtype="frame"),
                                                            list(type="integer",   cardinality=1),
                                                            list(type="integer",   cardinality=1)))
    } else if (schemaType == "df1c1")         { return(list(list(type="dataset",   cardinality=1,  dtype="frame"),
                                                            list(type="character", cardinality=1)))
    } else if (schemaType == "df1i1c1c1i1l1") { return(list(list(type="dataset",   cardinality=1,  dtype="frame"),
                                                            list(type="integer",   cardinality=1),
                                                            list(type="character", cardinality=1),
                                                            list(type="character", cardinality=1),
                                                            list(type="integer",   cardinality=1),
                                                            list(type="logical",   cardinality=1)))
    } else if (schemaType == "df1n1c1n1")     { return(list(list(type="dataset",   cardinality=1,  dtype="frame"),
                                                            list(type="numeric",   cardinality=1),
                                                            list(type="character", cardinality=1),
                                                            list(type="numeric",   cardinality=1)))
    } else if (schemaType == "dn1n1")         { return(list(list(type="dataset",   cardinality=1,  dtype="numeric"),
                                                            list(type="numeric",   cardinality=1)))
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
        # TODO: doesn't allow empty parameters in parameter string. all parameters need to be specified (in order).
        # allow optional parameters.
        if (featureParamsList[[i]] == "") { stop("All parameters must be specified!")}
        # parameters in the the parameter string should be valid R expressions
        p <- eval(parse(text=featureParamsList[[i]]))
        if (is.null(p))                                 { featureParamsList[i]   <- list(NULL)
        } else if (newPSchema[[i]]$type == "character") { featureParamsList[[i]] <- as.character(p)
        } else if (newPSchema[[i]]$type == "logical")   { featureParamsList[[i]] <- as.logical(p)
        } else if (newPSchema[[i]]$type == "integer")   { featureParamsList[[i]] <- as.integer(p)
        } else if (newPSchema[[i]]$type == "numeric")   { featureParamsList[[i]] <- as.numeric(p)
        }
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
                if (p$dtype == "frame") {
                    if (r) { d <- as.data.frame(h2o.getFrame(dataSets[[dataSetCount+1]]@key))
                    } else { d <- h2o.getFrame(dataSets[[dataSetCount+1]]@key)
                    }
                } else if (p$dtype == "numeric") { # we only take the first column of the dataset source file
                    d <- as.data.frame(h2o.getFrame(dataSets[[dataSetCount+1]]@key)[,1])[,1]
                    print("dataset dtype numeric")
                    print(d)
                }
                assign(parameterNameSpace[symbolCount+1], d, envir=env)
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
#' --------------- validate the return class of the h2o feature ---------------
#'
.validateH2OReturnClass<-
function(h2oRes, feature) {
    returnedClass <- class(h2oRes)
    allowableH2OReturnClasses <- .getAllowableH2OReturnClasses(feature)
    if (!(returnedClass %in% allowableH2OReturnClasses)) {
        stop(paste0("Returned class was: ", returnedClass, ", but expected: ", paste(allowableH2OReturnClasses,
                    collapse=", ")))
    }
    return(returnedClass)
}

.getAllowableH2OReturnClasses<-
function(feature) {
    if (feature == "cosine" || feature == "slice") {
        return(c("H2OFrame", "factor", "numeric", "character"))
    } else if (feature == "and") {
        return(c("H2OFrame", "numeric"))
    } else if (feature == "all") {
        return(c("logical"))
    } else if (feature == "asFactor" || feature == "cbind" || feature == "impute" || feature == "table") {
        return(c("H2OFrame"))
    } else if (feature == "colNames") {
        return(c("character"))
    } else if (feature == "histogram") {
        return(c("histogram"))
    } else if (feature == "quantile") {
        return(c("numeric"))
    } else {
        stop("Haven't decided what allowable return type for this feature is.")
    }
}

.validateRAndH2OResultComparability<-
function(h2oReturnClass, rReturnClass) {
    if        (h2oReturnClass == "H2OFrame"  && rReturnClass == "data.frame") {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "matrix")     {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "table")     {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "factor")     {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "numeric")     {
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "character")     {
    } else if (h2oReturnClass == "factor"    && rReturnClass == "factor")     {
    } else if (h2oReturnClass == "numeric"   && (rReturnClass %in% c("numeric", "integer", "double")))   {
    } else if (h2oReturnClass == "character" && rReturnClass == "character")  {
    } else if (h2oReturnClass == "logical"   && rReturnClass == "logical")    {
    } else {
        stop("H2O's and R's results cannot be compared because they are incompatible classes.")
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
    } else if (h2oReturnClass == "H2OFrame"  && rReturnClass == "factor")     { .compareFrameAndFactor(h2oRes, rRes)
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

.compareFrameAndFactor<-
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
.whatH2O<-
function(op) {
    if (op == "cosine")           { return("cos")
    } else if (op == "and")       { return("&")
    } else if (op == "all")       { return("all")
    } else if (op == "asFactor")  { return("as.factor")
    } else if (op == "cbind")     { return("h2o.cbind")
    } else if (op == "colNames")  { return("colnames")
    } else if (op == "slice")     { return("[")
    } else if (op == "histogram") { return("h2o.hist")
    } else if (op == "impute")    { return("h2o.impute")
    } else if (op == "table")     { return("h2o.table")
    } else if (op == "quantile")  { return("h2o.quantile")
    }
}

.whatR<-
function(op) {
    if (op == "cosine")           { return("cos")
    } else if (op == "and")       { return("&")
    } else if (op == "all")       { return("all")
    } else if (op == "asFactor")  { return("as.factor")
    } else if (op == "cbind")     { return("cbind")
    } else if (op == "colNames")  { return("colnames")
    } else if (op == "slice")     { return("[")
    } else if (op == "table")     { return("table")
    } else if (op == "quantile")  { return("quantile")
    }
}