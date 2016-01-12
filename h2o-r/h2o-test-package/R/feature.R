#'
#' --------------- execute a given test case ---------------
#'
h2oTest.executeFeatureTestCase<-
function(testCase) {

    dataSets <- .loadDataSets(testCase@dataSets)

    h2oFeatureParamsList <- .makeH2OFeatureParamsList(testCase@featureParams)

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined
    argsH2O <- .makeArgList(h2oFeatureParamsList, dataSets, testCase@feature, h2oEnv, FALSE)

    h2oRes <- do.call(what=testCase@feature, args=argsH2O, envir=h2oEnv)
    h2oTest.logInfo("The result of the H2O operation:")
    print(h2oRes)

    if (testCase@validationMethod == "R") {

        rFeatureParamsList <- .translateH2OFeatureParamsListToR(h2oFeatureParamsList, testCase@feature)

        rEnv <- new.env()
        argsR <- .makeArgList(rFeatureParamsList, dataSets, testCase@feature, rEnv, TRUE)

        rRes  <- do.call(what=.whatR(testCase@feature), args=argsR, envir=rEnv)
        h2oTest.logInfo("The result of the R operation:")
        print(rRes)

        .compare2Objects(h2oRes, rRes, testCase@feature)

    } else if (testCase@validationMethod == "H") {

        validationDataSet <- .loadDataSets(list(testCase@validationDataSet))[[1]]

        vFr <- h2o.getFrame(validationDataSet@key)
        h2oTest.logInfo("The hard-coded validation data set:")
        print(vFr)

        .compare2Objects(vFr, h2oRes)

    } else if (testCase@validationMethod == "O") {

        if (testCase@feature == "as.factor")        { .asFactorValidation(h2oRes)
        } else if (testCase@feature == "h2o.hist")  { .h2oHistValidation(h2oRes)
        }

    }

    h2oTest.pass()
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
#' --------------- return the data argument type for the given h2o or r feature ---------------
#'
.getDataArgsType<-
function(feature, r) {
    if (!r) {
        return("H2OFrame")
    } else {
        if (feature %in% c("as.factor", "cos", "all", "&", "h2o.cbind", "h2o.table", "colnames", "[", "h2o.hist",
                           "h2o.impute", "h2o.rep_len", "t", "h2o.var")) {
            return("data.frame")
        } else if (feature %in% c("h2o.quantile", "cut", "h2o.match")) {
            return("numeric")
        } else if (feature %in% c("h2o.which")) {
            return("logical")
        } else if (feature %in% c("h2o.strsplit", "h2o.toupper")) {
            return("character")
        } else if (feature %in% c("%*%")) {
            return("matrix")
        }
    }
}

.makeDataArg<-
function(dataSet, dataArgsType) {
    fr <- h2o.getFrame(dataSet@key)
    if        (dataArgsType == "H2OFrame")   { dArg <- fr
    } else if (dataArgsType == "data.frame") { dArg <- as.data.frame(fr)
    } else if (dataArgsType == "matrix")     { dArg <- as.matrix(as.data.frame(fr))
    } else if (dataArgsType == "numeric")    { dArg <- as.numeric(as.data.frame(fr[,1])[,1]) # we only take the first column of the dataset source file
    } else if (dataArgsType == "logical")    { dArg <- as.logical(as.data.frame(fr[,1])[,1])
    } else if (dataArgsType == "character")  { dArg <- as.character(as.data.frame(fr[,1])[,1])
    }
    return(dArg)
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

    dataArgsType <- .getDataArgsType(feature, r)

    for (d in dataSets) {
        dArg <- .makeDataArg(d, dataArgsType)
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
#' --------------- validation methods ---------------
#'
.compare2Objects<-
function(obj1, obj2, feature) {
    obj1Class <- class(obj1)
    obj2Class <- class(obj2)
    if        (obj1Class == "H2OFrame"  && obj2Class == "data.frame") { .comp.H2OFrame.data.frame(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "H2OFrame")   { .comp.H2OFrame.H2OFrame(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "matrix")     { .comp.H2OFrame.matrix(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "table")      { .comp.H2OFrame.table(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "factor")     { .comp.H2OFrame.base(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "integer")    { .comp.H2OFrame.base(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "logical")    { .comp.H2OFrame.base(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "character")  { .comp.H2OFrame.base(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "list")       {
        if (feature == "h2o.strsplit") {
            .comp.H2OFrame.data.frame(obj1, rbind.fill(lapply(obj2, function (r) { as.data.frame(t(r))})))
        } else {
            .comp.H2OFrame.base(obj1, obj2)
        }
    } else if (obj1Class == "factor"    && obj2Class == "factor")     { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "data.frame"&& obj2Class == "matrix")     { .comp.H2OFrame.matrix(obj1, obj2)
    } else if (obj1Class == "numeric"   && obj2Class == "numeric")    { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "character" && obj2Class == "character")  { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "logical"   && obj2Class == "logical")    { .comp.base.base(obj1, obj2)
    } else { stop(paste0("Objects of class ", obj1Class, " and ", obj2Class, " cannot be compared."))
    }
}

.comp.data.frame.data.frame<-
function(df1, df2) {
    nRowH <- nrow(df1)
    nColH <- ncol(df1)
    nRowR <- nrow(df2)
    nColR <- ncol(df2)

    if(nRowH != nRowR) {
        stop(paste0("Expected df1 and df2 results to have the same number of rows, but got: ", nRowH, " and ",
                    nRowR, ", respectively."))
    }
    if(nColH != nColR) {
        stop(paste0("Expected df1 and df2 results to have the same number of cols, but got: ", nColH, " and ",
                    nColR, ", respectively."))
    }
    #values
}
.comp.H2OFrame.H2OFrame <- function(hf1, hf2) { .comp.data.frame.data.frame(as.data.frame(hf1), as.data.frame(hf2)) }
.comp.H2OFrame.data.frame <- function(hf, df) { .comp.data.frame.data.frame(as.data.frame(hf), df) }
.comp.H2OFrame.matrix <- function(hf, m) { .comp.H2OFrame.data.frame(hf, as.data.frame(m)) }
.comp.H2OFrame.table<-
function(hf, table) {
    if (ncol(hf) > 2) { # case 2 dimensions
        .comp.H2OFrame.data.frame(hf[,2:ncol(hf)], as.data.frame.matrix(table))
    } else { # case 1 dimension
        .comp.H2OFrame.data.frame(hf[,2],as.data.frame(as.data.frame(table)[,2]))
    }
}
.comp.H2OFrame.base<-
function(hf, b) {
    numH2OCols <- ncol(hf)
    numH2ORows <- nrow(hf)
    if (class(b) == "list") {
        numListElements <- length(b)
        if (!(numH2OCols == numListElements)) {
            stop(paste0("Expected the H2OFrame to have the same number of columns as the base class result has list elements. ",
                        "H2OFrame cols: ", numH2OCols, " . Base class result list elements: ", numListElements))
        }
        numREntries <- length(b[[1]])
        if (!(numH2ORows == numREntries)) {
            stop(paste0("Expected the same number of h2o rows as list element length, but got: ", numH2ORows,
                        " and ", numREntries, ", respectively."))
        }
    } else {
        if (!(numH2OCols == 1)) {
            stop(paste0("Expected the H2OFrame to have 1 column, but got: ", numH2OCols))
        }
        numREntries   <- length(b)
        if (!(numH2ORows == numREntries)) {
            stop(paste0("Expected the same number of h2o rows as R entries, but got: ", numH2ORows, " and ",
                        numREntries, ", respectively."))
        }
        .comp.H2OFrame.H2OFrame(hf, as.h2o(b))
    }
}
.comp.base.base<-
function(b1, b2) {
    if (!all(b1 == b2)) {
        stop(paste0("Expected b1 and b2 results to be the same, but got: ", b1, " and ", b2,
                    ", respectively."))
    }
}

# special validation methods
.asFactorValidation<-
function(h2oRes) {
    if (!is.factor(h2oRes[,1])) {
        stop("Expected column 1 of h2o frame to be a factor, but it's not.")
    }
}
.h2oHistValidation<-
function(h2oRes) {
    print("Unimplemented!")
}



#'
#' --------------- get what for subsequent do.call ---------------
#'
.whatR<-
function(op) {
    if        (op == "cos")           { return("cos")
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
    } else if (op == "h2o.rep_len")   { return("rep_len")
    } else if (op == "h2o.strsplit")  { return("strsplit")
    } else if (op == "h2o.toupper")   { return("toupper")
    } else if (op == "t")             { return("t")
    } else if (op == "%*%")           { return("%*%")
    } else if (op == "h2o.var")       { return("var")
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