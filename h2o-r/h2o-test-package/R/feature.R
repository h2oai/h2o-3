#'
#' --------------- Execute a given test case ---------------
#'
#' Each test case has a feature, the name of which is the h2o R API name. In addition, test cases have named
#' parameters, as well as values associated with those parameters. Once again, the names associated with these
#' parameters are derived from the h2o R API. In the case of dataset parameters, the value corresponds to a dataset id.
#' General flow of execution is as follows:
#'
#' 1. Load the datasets associated with the test case into h2o.
#' 2. From the test case, make a named list of arguments to be passed to the h2o feature.
#' 3. Execute the h2o feature with the above-constructed arguments.
#' 4. Compare h2o's result to some "ground truth" result.
#' 4a. Ground truth results can come from a number of sources, but the primary sources are R-equivalent features and
#'     hard-coded datasets.
#' 4b. If the source of ground truth is an R-equivalent feature, then the R result is constructed in a similar manner
#'     as the h2o result, that is, we make a named list of arguments, which are then passed to the R function for
#'     execution.
#' 5. H2O's result is then compared to the ground truth result.
#'
h2oTest.executeFeatureTestCase<-
function(testCase) {

    h2oTest.logInfo("------------------------------------------------------------------------------------")
    h2oTest.logInfo("Loading test case datasets into H2O...")
    dataSets <- .loadDataSets(testCase@dataSets)
    h2oTest.logInfo("------------------------------------------------------------------------------------"); cat("\n\n")

    h2oFeatureParamsList <- .makeH2OFeatureParamsList(testCase@featureParams)

    h2oEnv <- new.env() # new environment for do.call, that has the h2o args defined
    argsH2O <- .makeArgList(h2oFeatureParamsList, dataSets, testCase@feature, h2oEnv, FALSE)

    h2oTest.logInfo("------------------------------------------------------------------------------------")
    h2oTest.logInfo("Executing do.call for an h2o feature...")
    h2oTest.logInfo(paste0("what: ", testCase@feature))
    h2oTest.logInfo("args: ")
    for (arg in argsH2O) { h2oTest.logInfo(paste0("    ", arg)) }
    h2oTest.logInfo("------------------------------------------------------------------------------------"); cat("\n\n")

    h2oRes <- do.call(what=testCase@feature, args=argsH2O, envir=h2oEnv)
    h2oTest.logInfo("------------------------------------------------------------------------------------")
    h2oTest.logInfo("The result of the H2O operation:")
    print(h2oRes)
    h2oTest.logInfo("------------------------------------------------------------------------------------"); cat("\n\n")

    if (testCase@validationMethod == "R") {

        rFeatureParamsList <- h2oFeatureParamsList
        if (testCase@feature == "h2o.quantile") { rFeatureParamsList <- h2oFeatureParamsList[1] }

        rEnv <- new.env()
        argsR <- .makeArgList(rFeatureParamsList, dataSets, testCase@feature, rEnv, TRUE)

        h2oTest.logInfo("------------------------------------------------------------------------------------")
        h2oTest.logInfo("Executing do.call for an R feature...")
        whatR <- .whatR(testCase@feature)
        h2oTest.logInfo(paste0("what: ", whatR))
        h2oTest.logInfo("args: ")
        for (arg in argsR) { h2oTest.logInfo(paste0("    ", arg)) }
        h2oTest.logInfo("------------------------------------------------------------------------------------")
        cat("\n\n")

        rRes  <- do.call(what=whatR, args=argsR, envir=rEnv)
        rResX <- .makeRResComparableToH2O(rRes, testCase@feature)
        h2oTest.logInfo("------------------------------------------------------------------------------------")
        h2oTest.logInfo("The result of the R operation:")
        print(rResX)
        h2oTest.logInfo("------------------------------------------------------------------------------------")
        cat("\n\n")

        h2oTest.logInfo("------------------------------------------------------------------------------------")
        h2oTest.logInfo("Comparing the h2o result to the (transformed) R result...")
        .compare2Objects(h2oRes, rResX)
        h2oTest.logInfo("------------------------------------------------------------------------------------")
        cat("\n\n")

    } else if (testCase@validationMethod == "H") {

        validationDataSet <- .loadDataSets(list(testCase@validationDataSet))[[1]]

        hardRes <- h2o.getFrame(validationDataSet@key)
        h2oTest.logInfo("The hard-coded result:")
        print(hardRes)

        h2oTest.logInfo("Transforming the hard-coded result, so that it can be compared to the h2o result properly...")
        hardResX <- .makeHardResComparableToH2O(hardRes, testCase@feature)

        h2oTest.logInfo("The transformed hard-coded result:")
        print(hardResX)

        h2oTest.logInfo("Comparing the h2o result to the (transformed) hard-coded result...")
        .compare2Objects(h2oRes, hardResX)

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
#' return `dataSets`, but with each dataSets's @key slot set to its key in h2o.
#'
.loadDataSets<-
function(dataSets) {
    lapply(dataSets, function(d) {
        if (grepl("smalldata",d@uri)) {
            dataSetPath <- h2oTest.locate(paste0("smalldata",strsplit(d@uri,"smalldata")[[1]][2]))
        } else if (grepl("bigdata",dataSet@uri)) {
            dataSetPath <- h2oTest.locate(paste0("bigdata",strsplit(d@uri,"bigdata")[[1]][2]))
        }

        h2oTest.logInfo(paste0("Loading dataset: ", dataSetPath))
        h2oDataSet <- h2o.importFile(dataSetPath)
        d@key <- attr(h2oDataSet,"id")
        d
    })
}


#'
#' --------------- Make a list of (non-dataset) arguments to be passed to the feature ---------------
#'
#' Parses the semi-colon-seperated feature parameters string into a list. Evaluates the RHS of the parameter token, so
#' it must be a valid R expression. Returns a named list, where the names are the parameter names and the values are
#' are the parameter values.
#'
.makeH2OFeatureParamsList<-
function(featureParamsString) {
    featureParamsList <- lapply(strsplit(featureParamsString,";")[[1]], function (p) {
        argNameValue <- strsplit(p,"=")[[1]]
        argName <- argNameValue[1]
        argValue <- eval(parse(text=argNameValue[2])) # parameter values should be valid R expressions
        list(name=argName, value=argValue)
    })

    if (length(featureParamsList) == 0) { return(featureParamsList) }
    return(featureParamsList)
}

#'
#' --------------- Get the type of the feature's data arguments ---------------
#'
#' For h2o, all data arguments are of type H2OFrame, but for R, the type
#' varies, depending on the feature. For example, base::toupper operates on a characeter vector, whereas
#' h2o::h2o.toupper operates on an H2OFrame. Since all datasets are initially ingested into h2o, when we construct
#' the data arguments to be passed to the R features, we have to convert them to their appropriate type. Return the
#' type of feature's data arguments.
#'
.getDataArgsType<-
function(feature, r) {
    if (!r) {
        return("H2OFrame")
    } else {
        if (feature %in% c("as.factor", "cos", "acos", "cosh", "acosh", "sin", "asin", "sinh", "asinh", "tan", "atan",
                           "tanh","atanh", "all", "any","&", "h2o.cbind", "colnames", "[", "h2o.hist", "h2o.impute",
                           "h2o.rep_len", "t", "h2o.var", "abs", "ceiling", "digamma", "exp", "gamma", "floor",
                           "expm1", "is.na", "lgamma", "log", "log2", "log1p", "log10", "!", "round", "sign",
                           "signif", "trigamma", "trunc", "ncol", "nrow", "sqrt", "|", "%%", "*", "-", "%/%", "scale",
                           "^", "+", ">=", ">", "<=", "<", "==", "!=", "/", "cumsum", "cummax", "cummin", "cumprod",
                           "max", "min", "prod", "sum")) {
            return("data.frame")
        } else if (feature %in% c("h2o.table")) {
            return("data.frameORvector")
        } else if (feature %in% c("h2o.quantile", "cut")) {
            return("numeric")
        } else if (feature %in% c("h2o.which")) {
            return("logical")
        } else if (feature %in% c("h2o.match", "is.character", "is.numeric", "h2o.levels", "h2o.nlevels", "ifelse",
                                  "mean", "h2o.median", "sd")) {
            return("vector")
        } else if (feature %in% c("h2o.strsplit", "h2o.toupper")) {
            return("character")
        } else if (feature %in% c("%*%")) {
            return("matrix")
        } else { stop(paste0("Unrecognized feature: ", feature, ". Cannot .getDataArgsType.")) }
    }
}

.makeDataArg<-
function(dataSet, dataArgsType) {
    fr <- h2o.getFrame(dataSet@key)
    if        (dataArgsType == "H2OFrame")   { dArg <- fr
    } else if (dataArgsType == "data.frame") { dArg <- as.data.frame(fr)
    } else if (dataArgsType == "vector")     { dArg <- as.data.frame(fr[,1])[,1]
    } else if (dataArgsType == "data.frameORvector") {
        if (ncol(fr) > 1) { dArg <- as.data.frame(fr)
        } else {            dArg <- as.data.frame(fr[,1])[,1] }
    } else if (dataArgsType == "matrix")     { dArg <- as.matrix(as.data.frame(fr))
    } else if (dataArgsType == "numeric")    { dArg <- as.numeric(as.data.frame(fr[,1])[,1])
    } else if (dataArgsType == "logical")    { dArg <- as.logical(as.data.frame(fr[,1])[,1])
    } else if (dataArgsType == "character")  { dArg <- as.character(as.data.frame(fr[,1])[,1])
    } else { stop("Unrecognized dataArgsType: ", dataArgsType, "! Cannot .makeDataArg.") }
    return(dArg)
}

#'
#' --------------- Make the named list of arguments to be passed to the feature ---------------
#'
#' 1. First, for data arguments, get their type. For h2o, all data arguments are of type H2OFrame, but for R, the type
#'    varies, depending on the feature. For example, base::toupper operates on a characeter vector, whereas
#'    h2o::h2o.toupper operates on an H2OFrame. Since all datasets are initially ingested into h2o, when we construct
#'    the data arguments to be passed to the R features, we have to convert them to their appropriate type.
#' 2. Next, for each data argument, transform it into the appropriate type, based upon data args type returned by
#'    .getDataArgsType. At this time no transformation is performed on h2o data args.
#' 3. For each data argument and each parameter argument, assign their values to variables in the environment that was
#'    passed to this function.
#' Return a named list, where each name is an argument name in the respective feature, and each value is a variable
#' (defined in env), which holds the argument's value.
#'
.makeArgList<-
function(featureParamsList, dataSets, feature, env, r) {

    argNames <- c()
    dataArgsType <- .getDataArgsType(feature, r)

    for (d in dataSets) {
        dArg <- .makeDataArg(d, dataArgsType)
        assign(d@dataArgName, dArg, envir=env)
        argNames <- c(argNames, d@dataArgName)
    }

    for (f in featureParamsList) {
        assign(f$name, f$value, envir=env)
        argNames <- c(argNames, f$name)
    }

    args <- lapply(argNames, function(s) { as.name(s) })

    if (r && feature == "[") { # h2o calls the slice parameters `data`, `row`, `col`, but r calls them `x`, `i`, `j`
        names(args) <- lapply(argNames, function(x) {
            if (x == "data") { "x"
            } else if (x == "row") { "i"
            } else if (x == "col") { "j"
            }
        })
    } else {
        names(args) <- argNames
    }

    return(args)
}

.makeRResComparableToH2O<-
function(rRes, feature) {
    if (feature == "h2o.strsplit") { return(rbind.fill(lapply(rRes, function (r) { as.data.frame(t(r))} ))) }

    rResClass <- class(rRes)
    if (rResClass == "data.frame" || rResClass == "matrix") {
        for (c in 1:ncol(rRes)) {
            if (is.logical(rRes[,c])) { rRes[,c] <- as.numeric(rRes[,c]) }
        }
    }
    return(rRes)
}

.makeHardResComparableToH2O<-
function(hardRes, feature) {
    if (feature == "all") {
        hardRes <- as.logical(as.data.frame(hardRes))
    }
    return(hardRes)
}


#'
#' --------------- validation methods ---------------
#'
.compare2Objects<-
function(obj1, obj2) {
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
    } else if (obj1Class == "H2OFrame"  && obj2Class == "numeric")    { .comp.H2OFrame.base(obj1, obj2)
    } else if (obj1Class == "H2OFrame"  && obj2Class == "list")       { .comp.H2OFrame.base(obj1, obj2)
    } else if (obj1Class == "character" && obj2Class == "H2OFrame")   { .comp.H2OFrame.base(obj2, obj1)
    } else if (obj1Class == "factor"    && obj2Class == "factor")     { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "data.frame"&& obj2Class == "matrix")     { .comp.H2OFrame.matrix(obj1, obj2)
    } else if (obj1Class == "numeric"   && obj2Class == "numeric")    { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "character" && obj2Class == "character")  { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "logical"   && obj2Class == "logical")    { .comp.base.base(obj1, obj2)
    } else if (obj1Class == "integer"   && obj2Class == "integer")    { .comp.base.base(obj1, obj2)
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
        stop(paste0("Expected the results to have the same number of rows, but got: ", nRowH, " and ",
                    nRowR, "."))
    }
    if(nColH != nColR) {
        stop(paste0("Expected the results to have the same number of cols, but got: ", nColH, " and ",
                    nColR, "."))
    }
    # max of 100 samples
    rowSamples <- sample(1:nRowH,min(10,nRowH))
    colSamples <- sample(1:nColH,min(10,nColH))
    for(r in rowSamples) {
        for(c in colSamples) {
            x <- df1[r,c]
            y <- df2[r,c]
            if (is.factor(x)) { x <- as.character(x) }
            if (is.factor(y)) { y <- as.character(y) }
            if (is.na(x)) { x <- as.numeric(x) }
            if (is.na(y)) { y <- as.numeric(y) }
            tryCatch({
                expect_equal(x, y, tolerance = 1e-6)
            }, error = function(e) {
                stop(paste0("Expected the values in row ", r, " and column ", c, " of the results to be equal, ",
                            "but got ", x, " and ", y))
            })
        }
    }

}
.comp.H2OFrame.H2OFrame <- function(hf1, hf2) { .comp.data.frame.data.frame(as.data.frame(hf1), as.data.frame(hf2)) }
.comp.H2OFrame.data.frame <- function(hf, df) { .comp.data.frame.data.frame(as.data.frame(hf), df) }
.comp.H2OFrame.matrix <- function(hf, m) { .comp.H2OFrame.data.frame(hf, as.data.frame(m)) }
.comp.H2OFrame.table<-
function(hf, table) {
    t <- data.frame()
    if (ncol(hf) > 2) { # case 2 dimensions
        for (r in rownames(table)) {
            for (c in colnames(table)) {
                if (table[r,c] >= 1) {
                    t <- rbind(t, c(r, c, table[r,c]))
                }
            }
        }
        t[,3] <- as.integer(t[,3])
        .comp.H2OFrame.base(hf[,3], t[,3])
    } else { # case 1 dimension
        for (r in names(table)) {
            t <- rbind(t, c(r, as.numeric(table[r])))
        }
        t[,2] <- as.integer(t[,2])
        .comp.H2OFrame.base(hf[,2], t[,2])
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
    }
    if (is.logical(b)) { b <- as.integer(b) }
    bf <- as.h2o(b)
    if (is.character(b)) { bf <- as.character(bf) }
    .comp.H2OFrame.H2OFrame(hf, bf)
}
.comp.base.base<-
function(b1, b2) {
    for (i in 1:length(b1)){
        tryCatch({
            expect_equal(b1[[i]], b2[[i]], tolerance = 1e-6)
        }, error = function(e) {
            stop(paste0("Expected b1 and b2 results to be the same, but got: ", b1[i], " and ", b2[i],
                                            ", respectively."))
        })
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
#' --------------- Translate the feature's name in the h2o R API to the appropriate R name ---------------
#'
.whatR<-
function(op) {
    if        (op == "h2o.cbind")     { return("cbind")
    } else if (op == "h2o.table")     { return("table")
    } else if (op == "h2o.quantile")  { return("quantile")
    } else if (op == "h2o.match")     { return("match")
    } else if (op == "h2o.which")     { return("which")
    } else if (op == "h2o.rep_len")   { return("rep_len")
    } else if (op == "h2o.strsplit")  { return("strsplit")
    } else if (op == "h2o.toupper")   { return("toupper")
    } else if (op == "h2o.var")       { return("var")
    } else if (op == "h2o.levels")    { return("levels")
    } else if (op == "h2o.nlevels")   { return("nlevels")
    } else if (op == "h2o.median")    { return("median")
    } else                            { return(op)
    }
}