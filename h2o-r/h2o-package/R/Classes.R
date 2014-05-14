#--------------------------------- Class Definitions ----------------------------------#
# WARNING: Do NOT touch the env slot! It is used to link garbage collection between R and H2O
setClass("H2OClient", representation(ip="character", port="numeric"), prototype(ip="127.0.0.1", port=54321))
setClass("H2ORawData", representation(h2o="H2OClient", key="character"))
# setClass("H2ORawData", representation(h2o="H2OClient", key="character", env="environment"))
setClass("H2OParsedData", representation(h2o="H2OClient", key="character", logic="logical"), prototype(logic=FALSE))
# setClass("H2OParsedData", representation(h2o="H2OClient", key="character", env="environment", logic="logical"), prototype(logic=FALSE))
setClass("H2OModel", representation(key="character", data="H2OParsedData", model="list", "VIRTUAL"))
# setClass("H2OModel", representation(key="character", data="H2OParsedData", model="list", env="environment", "VIRTUAL"))
setClass("H2OGrid", representation(key="character", data="H2OParsedData", model="list", sumtable="list", "VIRTUAL"))
setClass("H2OPerfModel", representation(cutoffs="numeric", measure="numeric", perf="character", model="list", roc="data.frame"))

setClass("H2OGLMModel", contains="H2OModel", representation(xval="list"))
# setClass("H2OGLMGrid", contains="H2OGrid")
setClass("H2OKMeansModel", contains="H2OModel")
setClass("H2ODeepLearningModel", contains="H2OModel", representation(valid="H2OParsedData"))
setClass("H2ODRFModel", contains="H2OModel", representation(valid="H2OParsedData"))
setClass("H2ONBModel", contains="H2OModel")
setClass("H2OPCAModel", contains="H2OModel")
setClass("H2OGBMModel", contains="H2OModel", representation(valid="H2OParsedData"))
setClass("H2OSpeeDRFModel", contains="H2OModel", representation(valid="H2OParsedData"))

setClass("H2OGLMGrid", contains="H2OGrid")
setClass("H2OGBMGrid", contains="H2OGrid")
setClass("H2OKMeansGrid", contains="H2OGrid")
setClass("H2ODRFGrid", contains="H2OGrid")
setClass("H2ODeepLearningGrid", contains="H2OGrid")

setClass("H2ORawDataVA", representation(h2o="H2OClient", key="character"))
# setClass("H2ORawDataVA", representation(h2o="H2OClient", key="character", env="environment"))
# setClass("H2OParsedDataVA", representation(h2o="H2OClient", key="character", env="environment"))
setClass("H2OParsedDataVA", contains="H2OParsedData")
setClass("H2OModelVA", representation(key="character", data="H2OParsedDataVA", model="list", "VIRTUAL"))
# setClass("H2OModelVA", representation(key="character", data="H2OParsedDataVA", model="list", env="environment", "VIRTUAL"))
setClass("H2OGridVA", representation(key="character", data="H2OParsedDataVA", model="list", sumtable="list", "VIRTUAL"))

setClass("H2OGLMModelVA", contains="H2OModelVA", representation(xval="list"))
setClass("H2OGLMGridVA", contains="H2OGridVA")
setClass("H2OKMeansModelVA", contains="H2OModelVA")
setClass("H2ORFModelVA", contains="H2OModelVA")

# Register finalizers for H2O data and model objects
# setMethod("initialize", "H2ORawData", function(.Object, h2o = new("H2OClient"), key = "") {
#   .Object@h2o = h2o
#   .Object@key = key
#   .Object@env = new.env()
#
#   assign("h2o", .Object@h2o, envir = .Object@env)
#   assign("key", .Object@key, envir = .Object@env)
#
#   # Empty keys don't refer to any object in H2O
#   if(key != "") reg.finalizer(.Object@env, .h2o.__finalizer)
#   return(.Object)
# })
#
# setMethod("initialize", "H2OParsedData", function(.Object, h2o = new("H2OClient"), key = "") {
#   .Object@h2o = h2o
#   .Object@key = key
#   .Object@env = new.env()
#
#   assign("h2o", .Object@h2o, envir = .Object@env)
#   assign("key", .Object@key, envir = .Object@env)
#
#   # Empty keys don't refer to any object in H2O
#   if(key != "") reg.finalizer(.Object@env, .h2o.__finalizer)
#   return(.Object)
# })
#
# setMethod("initialize", "H2OModel", function(.Object, key = "", data = new("H2OParsedData"), model = list()) {
#   .Object@key = key
#   .Object@data = data
#   .Object@model = model
#   .Object@env = new.env()
#
#   assign("h2o", .Object@data@h2o, envir = .Object@env)
#   assign("key", .Object@key, envir = .Object@env)
#
#   # Empty keys don't refer to any object in H2O
#   if(key != "") reg.finalizer(.Object@env, .h2o.__finalizer)
#   return(.Object)
# })

#--------------------------------- Class Display Functions ----------------------------------#
setMethod("show", "H2OClient", function(object) {
  cat("IP Address:", object@ip, "\n")
  cat("Port      :", object@port, "\n")
})

setMethod("show", "H2ORawData", function(object) {
  print(object@h2o)
  cat("Raw Data Key:", object@key, "\n")
})

setMethod("show", "H2OParsedData", function(object) {
  print(object@h2o)
  cat("Parsed Data Key:", object@key, "\n")
})

setMethod("show", "H2OGrid", function(object) {
  print(object@data)
  cat("Grid Search Model Key:", object@key, "\n")

  temp = data.frame(t(sapply(object@sumtable, c)))
  cat("\nSummary\n"); print(temp)
})

setMethod("show", "H2OGLMModel", function(object) {
  print(object@data)
  cat("GLM2 Model Key:", object@key)

  model = object@model
  cat("\n\nCoefficients:\n"); print(round(model$coefficients,5))
  if(!is.null(model$normalized_coefficients)) {
    cat("\nNormalized Coefficients:\n"); print(round(model$normalized_coefficients,5))
  }
  cat("\nDegrees of Freedom:", model$df.null, "Total (i.e. Null); ", model$df.residual, "Residual")
  cat("\nNull Deviance:    ", round(model$null.deviance,1))
  cat("\nResidual Deviance:", round(model$deviance,1), " AIC:", round(model$aic,1))
  cat("\nDeviance Explained:", round(1-model$deviance/model$null.deviance,5))
  cat("\nAvg Training Error Rate:", round(model$train.err,5), "\n")

  family = model$params$family$family
  if(family == "binomial") {
    cat("AUC:", round(model$auc,5), " Best Threshold:", round(model$best_threshold,5))
    cat("\n\nConfusion Matrix:\n"); print(model$confusion)
  }

  if(length(object@xval) > 0) {
    cat("\nCross-Validation Models:\n")
    if(family == "binomial") {
      modelXval = t(sapply(object@xval, function(x) { c(x@model$rank-1, x@model$auc, 1-x@model$deviance/x@model$null.deviance) }))
      colnames(modelXval) = c("Nonzeros", "AUC", "Deviance Explained")
    } else {
      modelXval = t(sapply(object@xval, function(x) { c(x@model$rank-1, x@model$aic, 1-x@model$deviance/x@model$null.deviance) }))
      colnames(modelXval) = c("Nonzeros", "AIC", "Deviance Explained")
    }
    rownames(modelXval) = paste("Model", 1:nrow(modelXval))
    print(modelXval)
  }
})

setMethod("show", "H2OKMeansModel", function(object) {
  print(object@data)
  cat("K-Means Model Key:", object@key)

  model = object@model
  cat("\n\nK-means clustering with", length(model$size), "clusters of sizes "); cat(model$size, sep=", ")
  cat("\n\nCluster means:\n"); print(model$centers)
  cat("\nClustering vector:\n"); print(summary(model$cluster))
  cat("\nWithin cluster sum of squares by cluster:\n"); print(model$withinss)
  cat("(between_SS / total_SS = ", round(100*sum(model$betweenss)/model$totss, 1), "%)\n")
  cat("\nAvailable components:\n\n"); print(names(model))
})

setMethod("show", "H2ODeepLearningModel", function(object) {
  print(object@data)
  cat("Deep Learning Model Key:", object@key)

  model = object@model
  cat("\n\nTraining classification error:", model$train_class_error)
  cat("\nTraining mean square error:", model$train_sqr_error)
  cat("\n\nValidation classification error:", model$valid_class_error)
  cat("\nValidation square error:", model$valid_sqr_error)
  if(!is.null(model$confusion)) {
    cat("\n\nConfusion matrix:\n"); cat("Reported on", object@valid@key, "\n"); print(model$confusion)
  }
})

setMethod("show", "H2ODRFModel", function(object) {
  print(object@data)
  cat("Distributed Random Forest Model Key:", object@key)

  model = object@model
  cat("\n\nClasification:", model$params$classification)
  cat("\nNumber of trees:", model$params$ntree)
  cat("\nTree statistics:\n"); print(model$forest)
  
  if(model$params$classification) {
    cat("\nConfusion matrix:\n"); cat("Reported on", object@valid@key, "\n")
    print(model$confusion)
    if(!is.null(model$auc) && !is.null(model$gini))
      cat("\nAUC:", model$auc, "\nGini:", model$gini, "\n")
  }
  if(!is.null(model$varimp)) {
    cat("\nVariable importance:\n"); print(model$varimp)
  }
  cat("\nMean-squared Error by tree:\n"); print(model$mse)
})

setMethod("show", "H2OSpeeDRFModel", function(object) {
  print(object@data)
  cat("SpeeDRF Model Key:", object@key)

  model = object@model
  cat("\n\nClassification:", model$params$classification)
  cat("\nNumber of trees:", model$params$ntree)
  
  if(FALSE){ #model$params$oobee) {
    cat("\nConfusion matrix:\n"); cat("Reported on oobee from", object@valid@key, "\n")
  } else {
    cat("\nConfusion matrix:\n"); cat("Reported on", object@valid@key,"\n")
  }
  print(model$confusion)
 
  if(!is.null(model$varimp)) {
    cat("\nVariable importance:\n"); print(model$varimp)
  }

  #mse <-model$mse[length(model$mse)] # (model$mse[is.na(model$mse) | model$mse <= 0] <- "")

  cat("\nMean-squared Error from the",model$params$ntree, "trees: "); cat(model$mse, "\n")
})

setMethod("show", "H2OPCAModel", function(object) {
  print(object@data)
  cat("PCA Model Key:", object@key)

  model = object@model
  cat("\n\nStandard deviations:\n", model$sdev)
  cat("\n\nRotation:\n"); print(model$rotation)
})

setMethod("show", "H2ONBModel", function(object) {
  print(object@data)
  cat("Naive Bayes Model Key:", object@key)
  
  model = object@model
  cat("\n\nA-priori probabilities:\n"); print(model$apriori_prob)
  cat("\n\nConditional probabilities:\n"); print(model$tables)
})

setMethod("show", "H2OGBMModel", function(object) {
  print(object@data)
  cat("GBM Model Key:", object@key, "\n")

  model = object@model
  if(model$params$distribution %in% c("multinomial", "bernoulli")) {
    cat("\nConfusion matrix:\nReported on", object@valid@key, "\n");
    print(model$confusion)
    
    if(!is.null(model$auc) && !is.null(model$gini))
      cat("\nAUC:", model$auc, "\nGini:", model$gini, "\n")
  }
  
  if(!is.null(model$varimp)) {
    cat("\nVariable importance:\n"); print(model$varimp)
  }
  cat("\nMean-squared Error by tree:\n"); print(model$err)
})

setMethod("show", "H2OPerfModel", function(object) {
  model = object@model
  tmp = t(data.frame(model[-length(model)]))
  rownames(tmp) = c("AUC", "Gini", "Best Cutoff", "F1", "Accuracy", "Precision", "Recall", "Specificity", "Max per Class Error")
  colnames(tmp) = "Value"; print(tmp)
  cat("\n\nConfusion matrix:\n"); print(model$confusion)
})

#--------------------------------- Unique H2O Methods ----------------------------------#
# TODO: s4 year, month impls as well?
h2o.year <- function(x){
  if( missing(x) ) stop('must specify x')
  if( !class(x) == 'H2OParsedData' ) stop('x must be an h2o data object')
  res1 <- .h2o.__unop2('year', x)
  .h2o.__binop2("-", res1, 1900)
}

h2o.month <- function(x){
  if( missing(x) ) stop('must specify x')
  if( !class(x) == 'H2OParsedData' ) stop('x must be an h2o data object')
  .h2o.__unop2('month', x)
}

year <- function(x) UseMethod('year', x)
year.H2OParsedData <- h2o.year
month <- function(x) UseMethod('month', x)
month.H2OParsedData <- h2o.month

diff.H2OParsedData <- function(x, lag = 1, differences = 1, ...) {
  if(!is.numeric(lag)) stop("lag must be numeric")
  if(!is.numeric(differences)) stop("differences must be numeric")
  
  expr = paste("diff(", paste(x@key, lag, differences, sep = ","), ")", sep = "")
  res = .h2o.__exec2(x@h2o, expr)
  new("H2OParsedData", h2o=x@h2o, key=res$dest_key, logic=FALSE)
}

as.h2o <- function(client, object, key = "", header, sep = "") {
  if(missing(client) || class(client) != "H2OClient") stop("client must be a H2OClient object")
  if(missing(object) || !is.numeric(object) && !is.data.frame(object)) stop("object must be numeric or a data frame")
  if(!is.character(key)) stop("key must be of class character")
  if(missing(key) || nchar(key) == 0) {
    key = paste(.TEMP_KEY, ".", .pkg.env$temp_count, sep="")
    .pkg.env$temp_count = (.pkg.env$temp_count + 1) %% .RESULT_MAX
  }
  
  # TODO: Be careful, there might be a limit on how long a vector you can define in console
  if(is.numeric(object) && is.vector(object)) {
    res <- .h2o.__exec2_dest_key(client, paste("c(", paste(object, sep=',', collapse=","), ")", collapse=""), key)
    return(new("H2OParsedData", h2o=client, key=res$dest_key))
  } else {
    tmpf <- tempfile(fileext=".csv")
    write.csv(object, file=tmpf, quote=F, row.names=F)
    h2f <- h2o.uploadFile(client, tmpf, key=key, header=header, sep=sep)
    unlink(tmpf)
    return(h2f)
  }
}

h2o.cut <- function(x, breaks) {
  if(missing(x)) stop("Must specify data set")
  if(!inherits(x, "H2OParsedData")) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))
  if(missing(breaks) || !is.numeric(breaks)) stop("breaks must be a numeric vector")
  
  nums = ifelse(length(breaks) == 1, breaks, paste("c(", paste(breaks, collapse=","), ")", sep=""))
  expr = paste("cut(", x@key, ",", nums, ")", sep="")
  res = .h2o.__exec2(x@h2o, expr)
  if(res$num_rows == 0 && res$num_cols == 0)   # TODO: If logical operator, need to indicate
    return(res$scalar)
  new("H2OParsedData", h2o=x@h2o, key=res$dest_key)
}

# TODO: H2O doesn't support any arguments beyond the single H2OParsedData object (with <= 2 cols)
h2o.table <- function(x) {
  if(missing(x)) stop("Must specify data set")
  if(!inherits(x, "H2OParsedData")) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))
  if(ncol(x) > 2) stop("Unimplemented")
  .h2o.__unop2("table", x)
}

h2o.ddply <- function (.data, .variables, .fun = NULL, ..., .progress = 'none'){
  if( missing(.data) ) stop('must specify .data')
  if( !(class(.data) %in% c('H2OParsedData', 'H2OParsedDataVA')) ) stop('.data must be an h2o data object')
  if( missing(.variables) ) stop('must specify .variables')
  if( missing(.fun) ) stop('must specify .fun')
  
  mm <- match.call()
  
  # we accept eg .(col1, col2), c('col1', 'col2'), 1:2, c(1,2)
  # as column names.  This is a bit complicated
  if( class(.variables) == 'character'){
    vars <- .variables
    idx <- match(vars, colnames(.data))
  } else if( class(.variables) == 'H2Oquoted' ){
    vars <- as.character(.variables)
    idx <- match(vars, colnames(.data))
  } else if( class(.variables) == 'quoted' ){ # plyr overwrote our . fn
    vars <- names(.variables)
    idx <- match(vars, colnames(.data))
  } else if( class(.variables) == 'integer' ){
    vars <- .variables
    idx <- .variables
  } else if( class(.variables) == 'numeric' ){   # this will happen eg c(1,2,3)
    vars <- .variables
    idx <- as.integer(.variables)
  }
  
  bad <- is.na(idx) | idx < 1 | idx > ncol(.data)
  if( any(bad) ) stop( sprintf('can\'t recognize .variables %s', paste(vars[bad], sep=',')) )
  
  fun_name <- mm[[ '.fun' ]]
  exec_cmd <- sprintf('ddply(%s,c(%s),%s)', .data@key, paste(idx, collapse=','), as.character(fun_name))
  res <- .h2o.__exec2(.data@h2o, exec_cmd)
  new('H2OParsedData', h2o=.data@h2o, key=res$dest_key)
}
ddply <- h2o.ddply

# TODO: how to avoid masking plyr?
`h2o..` <- function(...) {
  mm <- match.call()
  mm <- mm[-1]
  structure( as.list(mm), class='H2Oquoted')
}

`.` <- `h2o..`

h2o.addFunction <- function(object, fun, name){
  if( missing(object) || class(object) != 'H2OClient' ) stop('must specify h2o connection in object')
  if( missing(fun) ) stop('must specify fun')
  if( !missing(name) ){
    if( class(name) != 'character' ) stop('name must be a name')
    fun_name <- name
  } else {
    fun_name <- match.call()[['fun']]
  }
  src <- paste(deparse(fun), collapse='\n')
  exec_cmd <- sprintf('%s <- %s', as.character(fun_name), src)
  res <- .h2o.__exec2(object, exec_cmd)
}

h2o.unique <- function(x, incomparables = FALSE, ...){
  # NB: we do nothing with incomparables right now
  # NB: we only support MARGIN = 2 (which is the default)

  if(!class(x) %in% c('H2OParsedData', 'H2OParsedDataVA')) stop('h2o.unique: x is of the wrong type')
  if( nrow(x) == 0 | ncol(x) == 0) return(NULL) 
  if( nrow(x) == 1) return(x)

  args <- list(...)
  if( 'MARGIN' %in% names(args) && args[['MARGIN']] != 2 ) stop('h2o unique: only MARGIN 2 supported')
  .h2o.__unop2("unique", x)
  
#   uniq <- function(df){1}
#   h2o.addFunction(l, uniq)
#   res <- h2o.ddply(x, 1:ncol(x), uniq)
# 
#   res[,1:(ncol(res)-1)]
}
unique.H2OParsedData <- h2o.unique

h2o.runif <- function(x, min = 0, max = 1, seed = -1) {
  if(missing(x)) stop("Must specify data set")
  if(!inherits(x, "H2OParsedData")) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))
  if(!is.numeric(min)) stop("min must be a single number")
  if(!is.numeric(max)) stop("max must be a single number")
  if(length(min) > 1 || length(max) > 1) stop("Unimplemented")
  if(min > max) stop("min must be a number less than or equal to max")
  if(!is.numeric(seed)) stop("seed must be an integer >= 0")
  
  expr = paste("runif(", x@key, ",", seed, ")*(", max - min, ")+", min, sep = "")
  res = .h2o.__exec2(x@h2o, expr)
  if(res$num_rows == 0 && res$num_cols == 0)
    return(res$scalar)
  else
    return(new("H2OParsedData", h2o=x@h2o, key=res$dest_key, logic=FALSE))
}

h2o.anyFactor <- function(x) {
  # if(class(x) != "H2OParsedData") stop("x must be an H2OParsedData object")
  if(!inherits(x, "H2OParsedData")) stop("x must be an H2O parsed data object")
  as.logical(.h2o.__unop2("any.factor", x))
}

setMethod("colnames", "H2OParsedData", function(x, do.NULL = TRUE, prefix = "col") {
  if(!do.NULL) stop("Unimplemented: Auto-generated colnames are C1, C2, ...")
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key)
  unlist(lapply(res$cols, function(y) y$name))
})

#--------------------------------- Overloaded R Methods ----------------------------------#
#--------------------------------- Slicing ----------------------------------#
# i are the rows, j are the columns. These can be vectors of integers or character strings, or a single logical data object
setMethod("[", "H2OParsedData", function(x, i, j, ..., drop = TRUE) {
  numRows = nrow(x); numCols = ncol(x)
  if (!missing(j) && is.numeric(j) && any(abs(j) < 1 || abs(j) > numCols))
    stop("Array index out of bounds")

  if(missing(i) && missing(j)) return(x)
  if(missing(i) && !missing(j)) {
    if(is.character(j)) {
      # return(do.call("$", c(x, j)))
      myCol = colnames(x)
      if(any(!(j %in% myCol))) stop("Undefined columns selected")
      j = match(j, myCol)
    }
    # if(is.logical(j)) j = -which(!j)
    if(is.logical(j)) j = which(j)

    # if(class(j) == "H2OLogicalData")
    # if(class(j) == "H2OParsedData" && j@logic)
    if(inherits(j, "H2OParsedData") && j@logic)
      expr = paste(x@key, "[", j@key, ",]", sep="")
    else if(is.numeric(j) || is.integer(j))
      expr = paste(x@key, "[,c(", paste(j, collapse=","), ")]", sep="")
    else stop(paste("Column index of type", class(j), "unsupported!"))
  } else if(!missing(i) && missing(j)) {
    # if(is.logical(i)) i = -which(!i)
    if(is.logical(i)) i = which(i)
    # if(class(i) == "H2OLogicalData")
    # if(class(i) == "H2OParsedData" && i@logic)
    if(inherits(i, "H2OParsedData") && i@logic)
      expr = paste(x@key, "[", i@key, ",]", sep="")
    else if(is.numeric(i) || is.integer(i))
      expr = paste(x@key, "[c(", paste(i, collapse=","), "),]", sep="")
    else stop(paste("Row index of type", class(i), "unsupported!"))
  } else {
    # if(is.logical(i)) i = -which(!i)
    if(is.logical(i)) i = which(i)
    # if(class(i) == "H2OLogicalData") rind = i@key
    # if(class(i) == "H2OParsedData" && i@logic) rind = i@key
    if(inherits(i, "H2OParsedData") && i@logic) rind = i@key
    else if(is.numeric(i) || is.integer(i))
      rind = paste("c(", paste(i, collapse=","), ")", sep="")
    else stop(paste("Row index of type", class(i), "unsupported!"))

    if(is.character(j)) {
      # return(do.call("$", c(x, j)))
      myCol = colnames(x)
      if(any(!(j %in% myCol))) stop("Undefined columns selected")
      j = match(j, myCol)
    }
    # if(is.logical(j)) j = -which(!j)
    if(is.logical(j)) j = which(j)
    # if(class(j) == "H2OLogicalData") cind = j@key
    # if(class(j) == "H2OParsedData" && j@logic) cind = j@key
    if(inherits(j, "H2OParsedData") && j@logic) cind = j@key
    else if(is.numeric(j) || is.integer(j))
      cind = paste("c(", paste(j, collapse=","), ")", sep="")
    else stop(paste("Column index of type", class(j), "unsupported!"))
    expr = paste(x@key, "[", rind, ",", cind, "]", sep="")
  }
  res = .h2o.__exec2(x@h2o, expr)
  if(res$num_rows == 0 && res$num_cols == 0)
    res$scalar
  else
    new("H2OParsedData", h2o=x@h2o, key=res$dest_key)
})

setMethod("$", "H2OParsedData", function(x, name) {
  myNames = colnames(x)
  if(!(name %in% myNames)) return(NULL)
  cind = match(name, myNames)
  expr = paste(x@key, "[,", cind, "]", sep="")
  res = .h2o.__exec2(x@h2o, expr)
  if(res$num_rows == 0 && res$num_cols == 0)
    res$scalar
  else
    new("H2OParsedData", h2o=x@h2o, key=res$dest_key)
})

setMethod("[<-", "H2OParsedData", function(x, i, j, ..., value) {
  numRows = nrow(x); numCols = ncol(x)
  # if((!missing(i) && is.numeric(i) && any(abs(i) < 1 || abs(i) > numRows)) ||
  #     (!missing(j) && is.numeric(j) && any(abs(j) < 1 || abs(j) > numCols)))
  #  stop("Array index out of bounds!")
  if(!(missing(i) || is.numeric(i)) || !(missing(j) || is.numeric(j) || is.character(j)))
    stop("Row/column types not supported!")
  if(!inherits(value, "H2OParsedData") && !is.numeric(value))
    stop("value can only be numeric or a H2OParsedData object")
  if(is.numeric(value) && length(value) != 1 && length(value) != numRows)
    stop("value must be either a single number or a vector of length ", numRows)

  if(!missing(i) && is.numeric(i)) {
    if(any(i == 0)) stop("Array index out of bounds")
    if(any(i < 0 && abs(i) > numRows)) stop("Unimplemented: can't extend rows")
    if(min(i) > numRows+1) stop("new rows would leave holes after existing rows")
  }
  if(!missing(j) && is.numeric(j)) {
    if(any(j == 0)) stop("Array index out of bounds")
    if(any(j < 0 && abs(j) > numCols)) stop("Unimplemented: can't extend columns")
    if(min(j) > numCols+1) stop("new columns would leaves holes after existing columns")
  }

  if(missing(i) && missing(j))
    lhs = x@key
  else if(missing(i) && !missing(j)) {
    if(is.character(j)) {
      myNames = colnames(x)
      if(any(!(j %in% myNames))) {
        if(length(j) == 1)
          return(do.call("$<-", list(x, j, value)))
        else stop("Unimplemented: undefined column names specified")
      }
      cind = match(j, myNames)
      # cind = match(j[j %in% myNames], myNames)
    } else cind = j
    cind = paste("c(", paste(cind, collapse = ","), ")", sep = "")
    lhs = paste(x@key, "[,", cind, "]", sep = "")
  } else if(!missing(i) && missing(j)) {
      rind = paste("c(", paste(i, collapse = ","), ")", sep = "")
      lhs = paste(x@key, "[", rind, ",]", sep = "")
  } else {
    if(is.character(j)) {
      myNames = colnames(x)
      if(any(!(j %in% myNames))) stop("Unimplemented: undefined column names specified")
      cind = match(j, myNames)
      # cind = match(j[j %in% myNames], myNames)
    } else cind = j
    cind = paste("c(", paste(cind, collapse = ","), ")", sep = "")
    rind = paste("c(", paste(i, collapse = ","), ")", sep = "")
    lhs = paste(x@key, "[", rind, ",", cind, "]", sep = "")
  }

  # rhs = ifelse(class(value) == "H2OParsedData", value@key, paste("c(", paste(value, collapse = ","), ")", sep=""))
  if(inherits(value, "H2OParsedData"))
    rhs = value@key
  else
    rhs = ifelse(length(value) == 1, value, paste("c(", paste(value, collapse = ","), ")", sep=""))
  res = .h2o.__exec2(x@h2o, paste(lhs, "=", rhs))
  return(new("H2OParsedData", h2o=x@h2o, key=x@key))
})

setMethod("$<-", "H2OParsedData", function(x, name, value) {
  if(missing(name) || !is.character(name) || nchar(name) == 0)
    stop("name must be a non-empty string")
  if(!inherits(value, "H2OParsedData") && !is.numeric(value))
    stop("value can only be numeric or a H2OParsedData object")
  numCols = ncol(x); numRows = nrow(x)
  if(is.numeric(value) && length(value) != 1 && length(value) != numRows)
    stop("value must be either a single number or a vector of length ", numRows)
  myNames = colnames(x); idx = match(name, myNames)
 
  lhs = paste(x@key, "[,", ifelse(is.na(idx), numCols+1, idx), "]", sep = "")
  # rhs = ifelse(class(value) == "H2OParsedData", value@key, paste("c(", paste(value, collapse = ","), ")", sep=""))
  if(inherits(value, "H2OParsedData"))
    rhs = value@key
  else
    rhs = ifelse(length(value) == 1, value, paste("c(", paste(value, collapse = ","), ")", sep=""))
  res = .h2o.__exec2(x@h2o, paste(lhs, "=", rhs))
  
  if(is.na(idx))
    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES2, source=x@key, cols=numCols, comma_separated_list=name)
  return(new("H2OParsedData", h2o=x@h2o, key=x@key))
})

setMethod("[[", "H2OParsedData", function(x, i, exact = TRUE) {
  if(missing(i)) return(x)
  if(length(i) > 1) stop("[[]] may only select one column")
  if(!i %in% colnames(x) ) return(NULL)
  x[, i]
})

setMethod("[[<-", "H2OParsedData", function(x, i, value) {
  if( !inherits(value, 'H2OParsedData')) stop('Can only append H2O data to H2O data')
  if( ncol(value) > 1 ) stop('May only set a single column')
  if( nrow(value) != nrow(x) ) stop(sprintf('Replacement has %d row, data has %d', nrow(value), nrow(x)))

  mm <- match.call()
  col_name <- as.list(i)[[1]]

  cc <- colnames(x)
  if( col_name %in% cc ){
    x[, match( col_name, cc ) ] <- value
  } else {
    x <- cbind(x, value)
    cc <- c( cc, col_name )
    colnames(x) <- cc
  }
  x
})

# Note: right now, all things must be H2OParsedData
cbind.H2OParsedData <- function(..., deparse.level = 1) {
  if(deparse.level != 1) stop("Unimplemented")
  
  l <- list(...)
  # l_dep <- sapply(substitute(placeholderFunction(...))[-1], deparse)
  if(length(l) == 0) stop('cbind requires an H2O parsed dataset')
  
  klass <- 'H2OParsedData'
  h2o <- l[[1]]@h2o
  nrows <- nrow(l[[1]])
  m <- Map(function(elem){ inherits(elem, klass) & elem@h2o@ip == h2o@ip & elem@h2o@port == h2o@port & nrows == nrow(elem) }, l)
  compatible <- Reduce(function(l,r) l & r, x=m, init=T)
  if(!compatible){ stop(paste('cbind: all elements must be of type', klass, 'and in the same H2O instance'))}
  
  # If cbind(x,x), dupe colnames will automatically be renamed by H2O
  # TODO: cbind(df[,1], df[,2]) should retain colnames of original data frame (not temp keys from slice)
  if(is.null(names(l)))
    tmp <- Map(function(x) x@key, l)
  else
    tmp <- mapply(function(x,n) { ifelse(is.null(n) || is.na(n) || nchar(n) == 0, x@key, paste(n, x@key, sep = "=")) }, l, names(l))
  
  exec_cmd <- sprintf("cbind(%s)", paste(as.vector(tmp), collapse = ","))
  res <- .h2o.__exec2(h2o, exec_cmd)
  new('H2OParsedData', h2o=h2o, key=res$dest_key)
}

#--------------------------------- Arithmetic ----------------------------------#
setMethod("+", c("H2OParsedData", "missing"), function(e1, e2) { .h2o.__binop2("+", 0, e1) })
setMethod("-", c("H2OParsedData", "missing"), function(e1, e2) { .h2o.__binop2("-", 0, e1) })

setMethod("+", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("+", e1, e2) })
setMethod("-", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("-", e1, e2) })
setMethod("*", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("*", e1, e2) })
setMethod("/", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("/", e1, e2) })
setMethod("%%", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("%", e1, e2) })
setMethod("==", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("==", e1, e2) })
setMethod(">", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2(">", e1, e2) })
setMethod("<", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("<", e1, e2) })
setMethod("!=", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("!=", e1, e2) })
setMethod(">=", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2(">=", e1, e2) })
setMethod("<=", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("<=", e1, e2) })
setMethod("&", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("&", e1, e2) })
setMethod("|", c("H2OParsedData", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("|", e1, e2) })
setMethod("%*%", c("H2OParsedData", "H2OParsedData"), function(x, y) { .h2o.__binop2("%*%", x, y) })

setMethod("+", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("+", e1, e2) })
setMethod("-", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("-", e1, e2) })
setMethod("*", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("*", e1, e2) })
setMethod("/", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("/", e1, e2) })
setMethod("%%", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("%", e1, e2) })
setMethod("==", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("==", e1, e2) })
setMethod(">", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2(">", e1, e2) })
setMethod("<", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("<", e1, e2) })
setMethod("!=", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("!=", e1, e2) })
setMethod(">=", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2(">=", e1, e2) })
setMethod("<=", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("<=", e1, e2) })
setMethod("&", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("&", e1, e2) })
setMethod("|", c("numeric", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("|", e1, e2) })

setMethod("+", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("+", e1, e2) })
setMethod("-", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("-", e1, e2) })
setMethod("*", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("*", e1, e2) })
setMethod("/", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("/", e1, e2) })
setMethod("%%", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("%", e1, e2) })
setMethod("==", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("==", e1, e2) })
setMethod(">", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2(">", e1, e2) })
setMethod("<", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("<", e1, e2) })
setMethod("!=", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("!=", e1, e2) })
setMethod(">=", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2(">=", e1, e2) })
setMethod("<=", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("<=", e1, e2) })
setMethod("&", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("&", e1, e2) })
setMethod("|", c("H2OParsedData", "numeric"), function(e1, e2) { .h2o.__binop2("|", e1, e2) })

setMethod("&", c("logical", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("&", as.numeric(e1), e2) })
setMethod("|", c("logical", "H2OParsedData"), function(e1, e2) { .h2o.__binop2("|", as.numeric(e1), e2) })
setMethod("&", c("H2OParsedData", "logical"), function(e1, e2) { .h2o.__binop2("&", e1, as.numeric(e2)) })
setMethod("|", c("H2OParsedData", "logical"), function(e1, e2) { .h2o.__binop2("|", e1, as.numeric(e2)) })

setMethod("!", "H2OParsedData", function(x) { .h2o.__unop2("!", x) })
setMethod("abs", "H2OParsedData", function(x) { .h2o.__unop2("abs", x) })
setMethod("sign", "H2OParsedData", function(x) { .h2o.__unop2("sgn", x) })
setMethod("sqrt", "H2OParsedData", function(x) { .h2o.__unop2("sqrt", x) })
setMethod("ceiling", "H2OParsedData", function(x) { .h2o.__unop2("ceil", x) })
setMethod("floor", "H2OParsedData", function(x) { .h2o.__unop2("floor", x) })
setMethod("log", "H2OParsedData", function(x) { .h2o.__unop2("log", x) })
setMethod("exp", "H2OParsedData", function(x) { .h2o.__unop2("exp", x) })
setMethod("is.na", "H2OParsedData", function(x) { .h2o.__unop2("is.na", x) })
setMethod("t", "H2OParsedData", function(x) { .h2o.__unop2("t", x) })

setMethod("colnames<-", signature(x="H2OParsedData", value="H2OParsedData"),
  function(x, value) {
    if(class(value) == "H2OParsedDataVA") stop("value must be a FluidVecs object")
    else if(ncol(value) != ncol(x)) stop("Mismatched number of columns")
    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES2, source=x@key, copy_from=value@key)
    return(x)
})

setMethod("colnames<-", signature(x="H2OParsedData", value="character"),
  function(x, value) {
    if(any(nchar(value) == 0)) stop("Column names must be of non-zero length")
    else if(any(duplicated(value))) stop("Column names must be unique")
    else if(length(value) != (num = ncol(x))) stop(paste("Must specify a vector of exactly", num, "column names"))
    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES2, source=x@key, comma_separated_list=value)
    return(x)
})

setMethod("names", "H2OParsedData", function(x) { colnames(x) })
setMethod("names<-", "H2OParsedData", function(x, value) { colnames(x) <- value; return(x) })
# setMethod("nrow", "H2OParsedData", function(x) { .h2o.__unop2("nrow", x) })
# setMethod("ncol", "H2OParsedData", function(x) { .h2o.__unop2("ncol", x) })

setMethod("nrow", "H2OParsedData", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key); as.numeric(res$numRows) })

setMethod("ncol", "H2OParsedData", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key); as.numeric(res$numCols) })

setMethod("length", "H2OParsedData", function(x) {
  numCols = ncol(x)
  if (numCols == 1) {
    numRows = nrow(x)
    return (numRows)      
  }
  return (numCols)
})

setMethod("dim", "H2OParsedData", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key)
  as.numeric(c(res$numRows, res$numCols))
})

setMethod("dim<-", "H2OParsedData", function(x, value) { stop("Unimplemented") })

# setMethod("min", "H2OParsedData", function(x, ..., na.rm = FALSE) {
#   if(na.rm) stop("Unimplemented")
#   # res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key)
#   # min(..., sapply(res$cols, function(x) { x$min }), na.rm)
#   min(..., .h2o.__unop2("min", x), na.rm)
# })
#
# setMethod("max", "H2OParsedData", function(x, ..., na.rm = FALSE) {
#   if(na.rm) stop("Unimplemented")
#   # res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key)
#   # max(..., sapply(res$cols, function(x) { x$max }), na.rm)
#   max(..., .h2o.__unop2("max", x), na.rm)
# })

.min_internal <- min
min <- function(..., na.rm = FALSE) {
  # idx = sapply(c(...), function(y) { class(y) == "H2OParsedData" })
  idx = sapply(c(...), function(y) { class(y) %in% c("H2OParsedData", "H2OParsedDataVA") })
  
  if(any(idx)) {
    hex.op = ifelse(na.rm, "min.na.rm", "min")
    myVals = c(...); myData = myVals[idx]
    myKeys = sapply(myData, function(y) { y@key })
    expr = paste(hex.op, "(", paste(myKeys, collapse=","), ")", sep = "")
    res = .h2o.__exec2(myData[[1]]@h2o, expr)
    .Primitive("min")(unlist(myVals[!idx]), res$scalar, na.rm = na.rm)
  } else
    .Primitive("min")(..., na.rm = na.rm)
}

.max_internal <- max
max <- function(..., na.rm = FALSE) {
  # idx = sapply(c(...), function(y) { class(y) == "H2OParsedData" })
  idx = sapply(c(...), function(y) { class(y) %in% c("H2OParsedData", "H2OParsedDataVA") })
  
  if(any(idx)) {
    hex.op = ifelse(na.rm, "max.na.rm", "max")
    myVals = c(...); myData = myVals[idx]
    myKeys = sapply(myData, function(y) { y@key })
    expr = paste(hex.op, "(", paste(myKeys, collapse=","), ")", sep = "")
    res = .h2o.__exec2(myData[[1]]@h2o, expr)
    .Primitive("max")(unlist(myVals[!idx]), res$scalar, na.rm = na.rm)
  } else
    .Primitive("max")(..., na.rm = na.rm)
}

.sum_internal <- sum
sum <- function(..., na.rm = FALSE) {
  # idx = sapply(c(...), function(y) { class(y) == "H2OParsedData" })
  idx = sapply(c(...), function(y) { class(y) %in% c("H2OParsedData", "H2OParsedDataVA") })
  
  if(any(idx)) {
    hex.op = ifelse(na.rm, "sum.na.rm", "sum")
    myVals = c(...); myData = myVals[idx]
    myKeys = sapply(myData, function(y) { y@key })
    expr = paste(hex.op, "(", paste(myKeys, collapse=","), ")", sep = "")
    res = .h2o.__exec2(myData[[1]]@h2o, expr)
    .Primitive("sum")(unlist(myVals[!idx]), res$scalar, na.rm = na.rm)
  } else
    .Primitive("sum")(..., na.rm = na.rm)
}

setMethod("range", "H2OParsedData", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT2, src_key=x@key)
  temp = sapply(res$cols, function(x) { c(x$min, x$max) })
  c(min(temp[1,]), max(temp[2,]))
})

mean.H2OParsedData <- function(x, trim = 0, na.rm = FALSE, ...) {
  if(ncol(x) != 1 || trim != 0) stop("Unimplemented")
  if(h2o.anyFactor(x) || dim(x)[2] != 1) {
    warning("argument is not numeric or logical: returning NA")
    return(NA_real_)
  }
  if(!na.rm && .h2o.__unop2("any.na", x)) return(NA)
  .h2o.__unop2("mean", x)
}

setMethod("sd", "H2OParsedData", function(x, na.rm = FALSE) {
  if(ncol(x) != 1) stop("Unimplemented")
  if(dim(x)[2] != 1 || h2o.anyFactor(x)) stop("Could not coerce argument to double. H2O sd requires a single numeric column.")
  if(!na.rm && .h2o.__unop2("any.na", x)) return(NA)
  .h2o.__unop2("sd", x)
})

setMethod("var", "H2OParsedData", function(x, y = NULL, na.rm = FALSE, use) {
  if(!is.null(y) || !missing(use)) stop("Unimplemented")
  if(h2o.anyFactor(x)) stop("x cannot contain any categorical columns")
  if(!na.rm && .h2o.__unop2("any.na", x)) return(NA)
  .h2o.__unop2("var", x)
})

as.data.frame.H2OParsedData <- function(x, ...) {
  # Versions of R prior to 3.1 should not use hex string.
  # Versions of R including 3.1 and later should use hex string.
  use_hex_string = FALSE
  if (as.numeric(R.Version()$major) >= 3) {
    if (as.numeric(R.Version()$minor) >= 1) {
      use_hex_string = TRUE
    }
  }

  url <- paste('http://', x@h2o@ip, ':', x@h2o@port,
               '/2/DownloadDataset',
               '?src_key=', URLencode(x@key),
               '&hex_string=', as.numeric(use_hex_string),
               sep='')
  ttt <- getURL(url)
  n = nchar(ttt)

  # Delete last 1 or 2 characters if it's a newline.
  # Handle \r\n (for windows) or just \n (for not windows).
  chars_to_trim = 0
  if (n >= 2) {
      c = substr(ttt, n, n)
      if (c == "\n") {
          chars_to_trim = chars_to_trim + 1
      }
      if (chars_to_trim > 0) {
          c = substr(ttt, n-1, n-1)
          if (c == "\r") {
              chars_to_trim = chars_to_trim + 1
          }
      }    
  }

  if (chars_to_trim > 0) {
      ttt2 = substr(ttt, 1, n-chars_to_trim)
      # Is this going to use an extra copy?  Or should we assign directly to ttt?
      ttt = ttt2
  }
  
  # if((df.ncol = ncol(df)) != (x.ncol = ncol(x)))
  #  stop("Stopping conversion: Expected ", x.ncol, " columns, but data frame imported with ", df.ncol)
  # if(x.ncol > .MAX_INSPECT_COL_VIEW)
  #  warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  
  # Obtain the correct factor levels for each column
  # if(class(x) == "H2OParsedDataVA")
  #  res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS, key=x@key, max_column_display=.Machine$integer.max)
  # else
  #  res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS2, source=x@key, max_ncols=.Machine$integer.max)
  # colClasses = sapply(res$levels, function(x) { ifelse(is.null(x), "numeric", "factor") })

  # Substitute NAs for blank cells rather than skipping
  df = read.csv((tcon <- textConnection(ttt)), blank.lines.skip = FALSE, ...)
  # df = read.csv(textConnection(ttt), blank.lines.skip = FALSE, colClasses = colClasses, ...)
  close(tcon)
  return(df)
}

as.matrix.H2OParsedData <- function(x, ...) { as.matrix(as.data.frame(x, ...)) }

head.H2OParsedData <- function(x, n = 6L, ...) {
  numRows = nrow(x)
  stopifnot(length(n) == 1L)
  n <- ifelse(n < 0L, max(numRows + n, 0L), min(n, numRows))
  if(n == 0) return(data.frame())
  
  x.slice = as.data.frame(x[seq_len(n),])
#   if(ncol(x) > .MAX_INSPECT_COL_VIEW)
#     warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#   res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS2, source = x@key, max_ncols = .Machine$integer.max)
#   for(i in 1:ncol(x)) {
#     if(!is.null(res$levels[[i]]))
#       x.slice[,i] <- factor(x.slice[,i], levels = res$levels[[i]])
#   }
  return(x.slice)
}

tail.H2OParsedData <- function(x, n = 6L, ...) {
  stopifnot(length(n) == 1L)
  nrx <- nrow(x)
  n <- ifelse(n < 0L, max(nrx + n, 0L), min(n, nrx))
  if(n == 0) return(data.frame())
  
  idx = seq.int(to = nrx, length.out = n)
  x.slice = as.data.frame(x[idx,])
  rownames(x.slice) = idx
  
#   if(ncol(x) > .MAX_INSPECT_COL_VIEW)
#     warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#   res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS2, source = x@key, max_ncols = .Machine$integer.max)
#   for(i in 1:ncol(x)) {
#     if(!is.null(res$levels[[i]]))
#       x.slice[,i] <- factor(x.slice[,i], levels = res$levels[[i]])
#   }
  return(x.slice)
}

setMethod("as.factor", "H2OParsedData", function(x) { .h2o.__unop2("factor", x) })
setMethod("is.factor", "H2OParsedData", function(x) { as.logical(.h2o.__unop2("is.factor", x)) })

quantile.H2OParsedData <- function(x, probs = seq(0, 1, 0.25), na.rm = FALSE, names = TRUE, type = 7, ...) {
  if((numCols = ncol(x)) != 1) stop("quantile only operates on a single column")
  if(is.factor(x)) stop("factors are not allowed")
  if(!na.rm && .h2o.__unop2("any.na", x)) stop("missing values and NaN's not allowed if 'na.rm' is FALSE")
  if(!is.numeric(probs)) stop("probs must be a numeric vector")
  if(any(probs < 0 | probs > 1)) stop("probs must fall in the range of [0,1]")
  if(type != 2 && type != 7) stop("type must be either 2 (mean interpolation) or 7 (linear interpolation)")
  if(type != 7) stop("Unimplemented: Only type 7 (linear interpolation) is supported from the console")
  
  myProbs <- paste("c(", paste(probs, collapse = ","), ")", sep = "")
  expr = paste("quantile(", x@key, ",", myProbs, ")", sep = "")
  res = .h2o.__exec2(x@h2o, expr)
  # res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_QUANTILES, source_key = x@key, column = 0, quantile = paste(probs, collapse = ","), interpolation_type = type, ...)
  # col <- as.numeric(strsplit(res$result, "\n")[[1]][-1])
  # if(numCols > .MAX_INSPECT_COL_VIEW)
  #   warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  # res2 = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=res$dest_key, view=res$num_rows, max_column_display=.Machine$integer.max)
  # col <- sapply(res2$rows, function(x) { x[[2]] })
  col <- as.data.frame(new("H2OParsedData", h2o=x@h2o, key=res$dest_key))[[1]]
  if(names) names(col) <- paste(100*probs, "%", sep="")
  return(col)
}

# setMethod("summary", "H2OParsedData", function(object) {
summary.H2OParsedData <- function(object, ...) {
  digits = 12L
  if(ncol(object) > .MAX_INSPECT_COL_VIEW)
    warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_SUMMARY2, source=object@key, max_ncols=.Machine$integer.max)
  cols <- sapply(res$summaries, function(col) {
    if(col$stats$type != 'Enum') { # numeric column
      if(is.null(col$stats$mins) || length(col$stats$mins) == 0) col$stats$mins = NaN
      if(is.null(col$stats$maxs) || length(col$stats$maxs) == 0) col$stats$maxs = NaN
      if(is.null(col$stats$pctile))
        params = format(rep(signif(as.numeric(col$stats$mean), digits), 6), digits = 4)
      else
        params = format(signif(as.numeric(c(
          col$stats$mins[1],
          col$stats$pctile[4],
          col$stats$pctile[6],
          col$stats$mean,
          col$stats$pctile[8],
          col$stats$maxs[1])), digits), digits = 4)
      result = c(paste("Min.   :", params[1], "  ", sep=""), paste("1st Qu.:", params[2], "  ", sep=""),
                 paste("Median :", params[3], "  ", sep=""), paste("Mean   :", params[4], "  ", sep=""),
                 paste("3rd Qu.:", params[5], "  ", sep=""), paste("Max.   :", params[6], "  ", sep=""))
    }
    else {
      top.ix <- sort.int(col$hcnt, decreasing=TRUE, index.return=TRUE)$ix[1:6]
      if(is.null(col$hbrk)) domains <- top.ix[1:6] else domains <- col$hbrk[top.ix]
      counts <- col$hcnt[top.ix]
      
      # TODO: Make sure "NA's" isn't a legal domain level
      if(!is.null(col$nacnt) && col$nacnt > 0) {
        idx <- ifelse(any(is.na(top.ix)), which(is.na(top.ix))[1], 6)
        domains[idx] <- "NA's"
        counts[idx] <- col$nacnt
      }
      
      # width <- max(cbind(nchar(domains), nchar(counts)))
      width <- c(max(nchar(domains)), max(nchar(counts)))
      result <- paste(domains,
                      sapply(domains, function(x) { ifelse(width[1] == nchar(x), "", paste(rep(' ', width[1] - nchar(x)), collapse='')) }),
                      ":", 
                      sapply(counts, function(y) { ifelse(width[2] == nchar(y), "", paste(rep(' ', width[2] - nchar(y)), collapse='')) }),
                      counts,
                      " ",
                      sep='')
      # result[is.na(top.ix)] <- NA
      result[is.na(domains)] <- NA
      result
    }
  })
  # Filter out rows with nothing in them
  cidx <- apply(cols, 1, function(x) { any(!is.na(x)) })
  if(ncol(cols) == 1) { cols <- as.matrix(cols[cidx,]) } else { cols <- cols[cidx,] }
  # cols <- as.matrix(cols[cidx,])

  result = as.table(cols)
  rownames(result) <- rep("", nrow(result))
  colnames(result) <- sapply(res$summaries, function(col) col$colname)
  result
}

summary.H2OPCAModel <- function(object, ...) {
  # TODO: Save propVar and cumVar from the Java output instead of computing here
  myVar = object@model$sdev^2
  myProp = myVar/sum(myVar)
  result = rbind(object@model$sdev, myProp, cumsum(myProp))   # Need to limit decimal places to 4
  colnames(result) = paste("PC", seq(1, length(myVar)), sep="")
  rownames(result) = c("Standard deviation", "Proportion of Variance", "Cumulative Proportion")
  
  cat("Importance of components:\n")
  print(result)
}

screeplot.H2OPCAModel <- function(x, npcs = min(10, length(x@model$sdev)), type = "barplot", main = paste("h2o.prcomp(", x@data@key, ")", sep=""), ...) {
  if(type == "barplot")
    barplot(x@model$sdev[1:npcs]^2, main = main, ylab = "Variances", ...)
  else if(type == "lines")
    lines(x@model$sdev[1:npcs]^2, main = main, ylab = "Variances", ...)
  else
    stop("type must be either 'barplot' or 'lines'")
}

setMethod("ifelse", "H2OParsedData", function(test, yes, no) {
  # if(!(is.numeric(yes) || class(yes) == "H2OParsedData") || !(is.numeric(no) || class(no) == "H2OParsedData"))
  if(!(is.numeric(yes) || inherits(yes, "H2OParsedData")) || !(is.numeric(no) || inherits(no, "H2OParsedData")))
    stop("Unimplemented")
  if(!test@logic) stop(test@key, " is not a H2O logical data type")
  # yes = ifelse(class(yes) == "H2OParsedData", yes@key, yes)
  # no = ifelse(class(no) == "H2OParsedData", no@key, no)
  yes = ifelse(inherits(yes, "H2OParsedData"), yes@key, yes)
  no = ifelse(inherits(no, "H2OParsedData"), no@key, no)
  expr = paste("ifelse(", test@key, ",", yes, ",", no, ")", sep="")
  res = .h2o.__exec2(test@h2o, expr)
  if(res$num_rows == 0 && res$num_cols == 0)   # TODO: If logical operator, need to indicate
    res$scalar
  else
    new("H2OParsedData", h2o=test@h2o, key=res$dest_key, logic=FALSE)
})

setMethod("levels", "H2OParsedData", function(x) {
  if(ncol(x) != 1) return(NULL)
  res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS2, source = x@key, max_ncols = .Machine$integer.max)
  res$levels[[1]]
})

#----------------------------- Work in Progress -------------------------------#
# TODO: Need to change ... to environment variables and pass to substitute method,
#       Can't figure out how to access outside environment from within lapply
setMethod("apply", "H2OParsedData", function(X, MARGIN, FUN, ...) {
  if(missing(X) || !class(X) %in% c("H2OParsedData", "H2OParsedDataVA"))
   stop("X must be a H2O parsed data object")
  if(missing(MARGIN) || !(length(MARGIN) <= 2 && all(MARGIN %in% c(1,2))))
    stop("MARGIN must be either 1 (rows), 2 (cols), or a vector containing both")
  if(missing(FUN) || !is.function(FUN))
    stop("FUN must be an R function")
  
  myList <- list(...)
  if(length(myList) > 0) {
    stop("Unimplemented")
    tmp = sapply(myList, function(x) { !class(x) %in% c("H2OParsedData", "H2OParsedDataVA", "numeric") } )
    if(any(tmp)) stop("H2O only recognizes H2OParsedData and numeric objects")
    
    idx = which(sapply(myList, function(x) { class(x) %in% c("H2OParsedData", "H2OParsedDataVA") }))
    # myList <- lapply(myList, function(x) { if(class(x) %in% c("H2OParsedData", "H2OParsedDataVA")) x@key else x })
    myList[idx] <- lapply(myList[idx], function(x) { x@key })
    
    # TODO: Substitute in key name for H2OParsedData objects and push over wire to console
    if(any(names(myList) == ""))
      stop("Must specify corresponding variable names of ", myList[names(myList) == ""])
  }
  
  # Substitute in function name: FUN <- match.fun(FUN)
  myfun = deparse(substitute(FUN))
  len = length(myfun)
  if(len > 3 && substr(myfun[1], nchar(myfun[1]), nchar(myfun[1])) == "{" && myfun[len] == "}")
    myfun = paste(myfun[1], paste(myfun[2:(len-1)], collapse = ";"), "}")
  else
    myfun = paste(myfun, collapse = "")
  params = c(X@key, MARGIN, myfun)
  expr = paste("apply(", paste(params, collapse = ","), ")", sep="")
  res = .h2o.__exec2(X@h2o, expr)
  new("H2OParsedData", h2o=X@h2o, key=res$dest_key)
})

str.H2OParsedData <- function(object, ...) {
  if (length(l <- list(...)) && any("give.length" == names(l)))
    invisible(NextMethod("str", ...))
  else invisible(NextMethod("str", give.length = FALSE, ...))
  
  if(ncol(object) > .MAX_INSPECT_COL_VIEW)
    warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_INSPECT, key=object@key, max_column_display=.Machine$integer.max)
  cat("\nH2O dataset '", object@key, "':\t", res$num_rows, " obs. of  ", (p <- res$num_cols), 
      " variable", if(p != 1) "s", if(p > 0) ":", "\n", sep = "")
  
  cc <- unlist(lapply(res$cols, function(y) y$name))
  width <- max(nchar(cc))
  rows <- res$rows[1:min(res$num_rows, 10)]    # TODO: Might need to check rows > 0
  
  if(class(object) == "H2OParsedDataVA")
    res2 = .h2o.__remoteSend(object@h2o, .h2o.__HACK_LEVELS, key=object@key, max_column_display=.Machine$integer.max)
  else
    res2 = .h2o.__remoteSend(object@h2o, .h2o.__HACK_LEVELS2, source=object@key, max_ncols=.Machine$integer.max)
  for(i in 1:p) {
    cat("$ ", cc[i], rep(' ', width - nchar(cc[i])), ": ", sep = "")
    rhead <- sapply(rows, function(x) { x[i+1] })
    if(is.null(res2$levels[[i]]))
      cat("num  ", paste(rhead, collapse = " "), if(res$num_rows > 10) " ...", "\n", sep = "")
    else {
      rlevels = res2$levels[[i]]
      cat("Factor w/ ", (count <- length(rlevels)), " level", if(count != 1) "s", ' "', paste(rlevels[1:min(count, 2)], collapse = '","'), '"', if(count > 2) ",..", ": ", sep = "")
      cat(paste(match(rhead, rlevels), collapse = " "), if(res$num_rows > 10) " ...", "\n", sep = "")
    }
  }
}

setMethod("findInterval", "H2OParsedData", function(x, vec, rightmost.closed = FALSE, all.inside = FALSE) {
  if(any(is.na(vec)))
    stop("'vec' contains NAs")
  if(is.unsorted(vec))
    stop("'vec' must be sorted non-decreasingly")
  if(all.inside) stop("Unimplemented")
  
  myVec = paste("c(", .seq_to_string(vec), ")", sep = "")
  expr = paste("findInterval(", x@key, ",", myVec, ",", as.numeric(rightmost.closed), ")", sep = "")
  res = .h2o.__exec2(x@h2o, expr)
  new('H2OParsedData', h2o=x@h2o, key=res$dest_key)
})

# setGeneric("histograms", function(object) { standardGeneric("histograms") })
# setMethod("histograms", "H2OParsedData", function(object) {
#   if(ncol(object) > .MAX_INSPECT_COL_VIEW)
#     warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#   res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_SUMMARY2, source=object@key, max_ncols=.Machine$integer.max)
#   list.of.bins <- lapply(res$summaries, function(x) {
#     if (x$stats$type == 'Enum') {
#       bins <- NULL
#     } else {
#       counts <- x$hcnt
#       breaks <- seq(x$hstart, by=x$hstep, length.out=length(x$hcnt) + 1)
#       bins <- list(counts,breaks)
#       names(bins) <- cbind('counts', 'breaks')
#     }
#     bins
#   })
#   return(list.of.bins)
# })

#--------------------------------- ValueArray ----------------------------------#
setMethod("show", "H2ORawDataVA", function(object) {
  print(object@h2o)
  cat("Raw Data Key:", object@key, "\n")
})

setMethod("show", "H2OParsedDataVA", function(object) {
  print(object@h2o)
  cat("Parsed Data Key:", object@key, "\n")
})

setMethod("show", "H2OGLMModelVA", function(object) {
  print(object@data)
  cat("GLM Model Key:", object@key, "\n\n")
  
  model = object@model
  cat("Coefficients:\n"); print(round(model$coefficients,5))
  if(!is.null(model$normalized_coefficients)) {
    cat("\nNormalized Coefficients:\n"); print(round(model$normalized_coefficients,5))
  }
  cat("\nDegrees of Freedom:", model$df.null, "Total (i.e. Null); ", model$df.residual, "Residual")
  cat("\nNull Deviance:    ", round(model$null.deviance,1))
  cat("\nResidual Deviance:", round(model$deviance,1), " AIC:", round(model$aic,1))
  cat("\nDeviance Explained:", round(1-model$deviance/model$null.deviance,5))
  cat("\nAvg Training Error Rate:", round(model$train.err,5), "\n")

  family = model$params$family$family
  if(family == "binomial") {
    cat("AUC:", round(model$auc,5), " Best Threshold:", round(model$threshold,5))
    cat("\n\nConfusion Matrix:\n"); print(model$confusion)
  }

  if(length(object@xval) > 0) {
    cat("\nCross-Validation Models:\n")
    if(family == "binomial") {
      modelXval = t(sapply(object@xval, function(x) { c(x@model$threshold, x@model$auc, x@model$class.err) }))
      colnames(modelXval) = c("Best Threshold", "AUC", "Err(0)", "Err(1)")
    } else {
      modelXval = sapply(object@xval, function(x) { x@model$train.err })
      modelXval = data.frame(modelXval)
      colnames(modelXval) = c("Error")
    }
    rownames(modelXval) = paste("Model", 0:(nrow(modelXval)-1))
    print(modelXval)
  }
})

setMethod("show", "H2OGridVA", function(object) {
  print(object@data)
  cat("Grid Search Model Key:", object@key, "\n")
  
  temp = data.frame(t(sapply(object@sumtable, c)))
  cat("\nSummary\n"); print(temp)
})

setMethod("show", "H2OKMeansModelVA", function(object) {
  print(object@data)
  cat("K-Means Model Key:", object@key)
  
  model = object@model
  cat("\n\nK-means clustering with", length(model$size), "clusters of sizes "); cat(model$size, sep=", ")
  cat("\n\nCluster means:\n"); print(model$centers)
  cat("\nClustering vector:\n"); print(summary(model$cluster))
  cat("\nWithin cluster sum of squares by cluster:\n"); print(model$withinss)
  cat("(between_SS / total_SS = ", round(100*sum(model$betweenss)/model$totss, 1), "%)\n")
  cat("\nAvailable components:\n"); print(names(model))
})

setMethod("show", "H2ORFModelVA", function(object) {
  print(object@data)
  cat("Random Forest Model Key:", object@key)

  model = object@model
  cat("\n\nClassification Error:", model$classification_error)
  cat("\nConfusion Matrix:\n"); print(model$confusion)
  cat("\nTree Stats:\n"); print(model$tree_sum)
})

setMethod("colnames", "H2OParsedDataVA", function(x) {
  if(ncol(x) > .MAX_INSPECT_COL_VIEW)
    warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=x@key, max_column_display=.Machine$integer.max)
  unlist(lapply(res$cols, function(y) y$name))
})

setMethod("colnames<-", signature(x="H2OParsedDataVA", value="H2OParsedDataVA"), 
  function(x, value) { res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES, target=x@key, copy_from=value@key); return(x) })

setMethod("colnames<-", signature(x="H2OParsedDataVA", value="character"),
  function(x, value) {
    if(any(nchar(value) == 0)) stop("Column names must be of non-zero length")
    else if(any(duplicated(value))) stop("Column names must be unique")
    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES, target=x@key, comma_separated_list=value)
    return(x)
})

setMethod("names", "H2OParsedDataVA", function(x) { colnames(x) })
setMethod("names<-", "H2OParsedDataVA", function(x, value) { colnames(x) <- value })

setMethod("nrow", "H2OParsedDataVA", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=x@key); as.numeric(res$num_rows) })

setMethod("ncol", "H2OParsedDataVA", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=x@key); as.numeric(res$num_cols) })

setMethod("dim", "H2OParsedDataVA", function(x) {
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=x@key)
  as.numeric(c(res$num_rows, res$num_cols))
})

head.H2OParsedDataVA <- function(x, n = 6L, ...) {
  numRows = nrow(x)
  stopifnot(length(n) == 1L)
  n <- ifelse(n < 0L, max(numRows + n, 0L), min(n, numRows))
  if(n == 0) return(data.frame())
  if(n > .MAX_INSPECT_ROW_VIEW) stop(paste("Cannot view more than", .MAX_INSPECT_ROW_VIEW, "rows"))
  if(ncol(x) > .MAX_INSPECT_COL_VIEW)
    warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=x@key, offset=0, view=n, max_column_display=.Machine$integer.max)
  res2 = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS, key=x@key, max_column_display=.Machine$integer.max)
  blanks = sapply(res$cols, function(y) { nchar(y$name) == 0 })   # Must stop R from auto-renaming cols with no name
  nums = sapply(res2$levels, is.null)   # Must stop R from coercing all columns with "NA" to factors, confusing rbind if it is actually numeric
  
  temp = lapply(res$rows, function(y) { y$row = NULL; na_num = (y[nums] == "NA"); y[nums][na_num] = as.numeric(NA);
                                        tmp = as.data.frame(y); names(tmp)[blanks] = ""; return(tmp) })
  if(is.null(temp)) return(temp)
  x.slice = do.call(rbind, temp)
  
  for(i in 1:ncol(x)) {
    if(!is.null(res2$levels[[i]]))
      x.slice[,i] <- factor(x.slice[,i], levels = res2$levels[[i]])
  }
  return(x.slice)
}

tail.H2OParsedDataVA <- function(x, n = 6L, ...) {
  stopifnot(length(n) == 1L)
  nrx <- nrow(x)
  n <- ifelse(n < 0L, max(nrx + n, 0L), min(n, nrx))
  if(n == 0) return(data.frame())
  if(n > .MAX_INSPECT_ROW_VIEW) stop(paste("Cannot view more than", .MAX_INSPECT_ROW_VIEW, "rows"))
  if(ncol(x) > .MAX_INSPECT_COL_VIEW)
    warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  
  idx = seq.int(to = nrx, length.out = n)
  res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=x@key, offset=idx[1], view=length(idx), max_column_display=.Machine$integer.max)
  res2 = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS, key=x@key, max_column_display=.Machine$integer.max)
  blanks = sapply(res$cols, function(y) { nchar(y$name) == 0 })   # Must stop R from auto-renaming cols with no name
  nums = sapply(res2$levels, is.null)   # Must stop R from coercing all columns with "NA" to factors, confusing rbind if it is actually numeric
  
  temp = lapply(res$rows, function(y) { y$row = NULL; na_num = (y[nums] == "NA"); y[nums][na_num] = as.numeric(NA);
                                        tmp = as.data.frame(y); names(tmp)[blanks] = ""; return(tmp) })
  if(is.null(temp)) return(temp)
  x.slice = do.call(rbind, temp)
  rownames(x.slice) = idx
  
  for(i in 1:ncol(x)) {
    if(!is.null(res2$levels[[i]]))
      x.slice[,i] <- factor(x.slice[,i], levels = res2$levels[[i]])
  }
  return(x.slice)
}

summary.H2OParsedDataVA <- function(object, ...) {
  if(ncol(object) > .MAX_INSPECT_COL_VIEW)
    warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_SUMMARY, key=object@key, max_column_display=.Machine$integer.max)
  cols <- sapply(res$summary$columns, function(col) {
    if(col$type == "number") {
      if(is.null(col$min) || length(col$min) == 0) col$min = NaN
      if(is.null(col$max) || length(col$max) == 0) col$max = NaN
      if(is.null(col$mean) || length(col$mean) == 0) col$mean = NaN
      if(is.null(col$percentiles))
        params = format(rep(round(as.numeric(col$mean), 3), 6))
      else
        params = format(round(as.numeric(c(col$min[1], col$percentiles$values[4], col$percentiles$values[6], col$mean, col$percentiles$values[8], col$max[1])), 3))
      result <- c(paste("Min.   :", params[1], "  ", sep=""), paste("1st Qu.:", params[2], "  ", sep=""),
                  paste("Median :", params[3], "  ", sep=""), paste("Mean   :", params[4], "  ", sep=""),
                  paste("3rd Qu.:", params[5], "  ", sep=""), paste("Max.   :", params[6], "  ", sep=""))
    }
    else if(col$type == "enum") {
      rhist = col$histogram
      top.ix = sort.int(rhist$bins, decreasing=T, index.return=T)$ix[1:6]
      
      counts = rhist$bins[top.ix]
      if(is.null(rhist$bin_names)) domains = top.ix[1:6] else domains = rhist$bin_names[top.ix]
      
      # TODO: Make sure "NA's" isn't a legal domain level
      if(!is.null(col$na) && col$na > 0) {
        idx <- ifelse(any(is.na(top.ix)), which(is.na(top.ix))[1], 6)
        domains[idx] <- "NA's"
        counts[idx] <- col$na
      }
      
      width <- c(max(nchar(domains)), max(nchar(counts)))
      result <- paste(domains,
                      sapply(domains, function(x) { nspaces = width[1] - nchar(x); ifelse(nspaces == 0, "", paste(rep(' ', nspaces), collapse='')) }),
                      ":", 
                      sapply(counts, function(y) { nspaces = width[2] - nchar(y); ifelse(nspaces == 0, "", paste(rep(' ', nspaces), collapse='')) }),
                      counts,
                      " ",
                      sep='')
      result[is.na(domains)] <- NA
      result
    }
  })
  # Filter out rows with nothing in them
  cidx <- apply(cols, 1, function(x) { any(!is.na(x)) })
  if(ncol(cols) == 1) { cols <- as.matrix(cols[cidx,]) } else { cols <- cols[cidx,] }
  # cols <- cols[cidx,]
  
  result = as.table(cols)
  rownames(result) <- rep("", nrow(result))
  colnames(result) <- sapply(res$summary$columns, function(col) col$name)
  result
}
