#'
#' Class definitions and their `show` methods.
#'
#'
#' To conveniently and safely pass messages between R and H2O, this package relies
#' on S4 objects to capture and pass state. This R file contains all of the h2o
#' package's classes as well as their complementary `show` methods. The end user
#' will typically never have to reason with these objects directly, as there are
#' S3 accessor methods provided for creating new objects.
#'
#' The S4 classes used by the h2o package are grouped into three divisions,
#' namely the '"FluidVec"' (FV), '"ValueArray"' (VA), and '"AST"' groups.
#'
#'      1. Group '"FluidVec"':
#'          FluidVec (or FV for short) is an H2O specific internal
#'          representation of the data. Data is stored in columns (vectors),
#'          and vectors are sliced into chunks, distributed around the cluster.
#'
#'          FluidVec replaced the row-based representation known as ValueArray
#'          (another H2O specific data format that is described below), and is
#'          now the dominant data type used by algorithms and analytical
#'          operations in H2O.
#'
#'      2. Group '"ValueArray"':
#'          ValueArray (or VA for short) is a legacy H2O specific internal
#'          representation of the data. Data is striped row-wise into chunks,
#'          which are distributed across the cluster.
#'
#'      3. Group '"AST"':
#'          R expressions involving objects from group '"FluidVec"' or group
#'          '"ValueArray"' are _lazily evaluated_. Therefore, when assigning
#'          to a new R variable, the R variable represents a _promise_ to
#'          evaluate the expression on demand. The usual lexical scoping rules
#'          of R apply to these objects.
#'
#'
#' Note: <WARNING> Do NOT touch the env slot! It is used to link garbage collection between R and H2O

#-----------------------------------------------------------------------------------------------------------------------
# FluidVec Class Defintions
#-----------------------------------------------------------------------------------------------------------------------
#'
#' The H2OClient class.
#'
#' This class represents a connection to the H2O Cloud.
#'
#' Because H2O is not a master-slave architecture, there is no restriction on which H2O node
#' is used to establish the connection between R (the client) and H2O (the server).
#'
#' A new H2O connection is established via the h2o.init() function, which takes as parameters
#' the `ip` and `port` of the machine running an instance to connect with. The default behavior
#' is to connect with a local instance of H2O at port 54321, or to boot a new local instance if one
#' is not found at port 54321.
setClass("H2OClient", representation(ip="character", port="numeric"), prototype(ip="127.0.0.1", port=54321))

setMethod("show", "H2OClient", function(object) {
  cat("IP Address:", object@ip,   "\n")
  cat("Port      :", object@port, "\n")
})

#'
#' The H2ORawData class.
#'
#' This class represents data in a post-import format.
#'
#' Data ingestion is a two-step process in H2O. First, a given path to a data source is _imported_ for validation by the
#' user. The user may continue onto _parsing_ all of the data into memory, or the user may choose to back out and make
#' corrections. Imported data is in a staging area such that H2O is aware of the data, but the data is not yet in
#' memory.
#'
#' The H2ORawData is a representation of the imported, not yet parsed, data.
setClass("H2ORawData", representation(h2o="H2OClient", key="character"))

setMethod("show", "H2ORawData", function(object) {
  print(object@h2o)
  cat("Raw Data Key:", object@key, "\n")
})

#'
#' The H2OFrame class.
#'
#' An H2OFrame is a virtual class that is the ancestor of all AST and H2OParsedData objects.
setClass("H2OFrame", contains="VIRTUAL")

# No show method for this type of object.

#'
#' The H2OParsedData class.
#'
#' This is a common data type used by the h2o package. An object of type H2OParsedData has slots
#' for the h2o cloud it belongs to (an object of type H2OClient), the key that resides in the
#' H2O cloud, and whether or not is a logical data type.
#'
#' The slot `key` is a character string _of the same name_ as the key that resides in the H2O cloud.
#'
#' This class inherits from H2OFrame.
setClass("H2OParsedData", representation(h2o="H2OClient", key="character", logic="logical"), prototype(logic=FALSE), contains="H2OFrame")

setMethod("show", "H2OParsedData", function(object) {
  print(object@h2o)
  cat("Parsed Data Key:", object@key, "\n")
})

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OParsedData).
setClass("H2OModel", representation(key="character", data="H2OParsedData", model="list", "VIRTUAL"))

#'
#' The H2OGrid class.
#'
#' This virtual class represents a grid search performed by H2O.
#'
#' A grid search is an automated procedure for varying the parameters of a model and discovering the best tunings.
setClass("H2OGrid", representation(key="character",   data="H2OParsedData", model="list", sumtable="list", "VIRTUAL"))

setMethod("show", "H2OGrid", function(object) {
  print(object@data)
  cat("Grid Search Model Key:", object@key, "\n")

  temp = data.frame(t(sapply(object@sumtable, c)))
  cat("\nSummary\n"); print(temp)
})

#'
#' The H2OPerfModel class.
#'
#' This class represents the output of the evaluation of a binary classification model.
setClass("H2OPerfModel", representation(cutoffs="numeric", measure="numeric", perf="character", model="list", roc="data.frame"))

setMethod("show", "H2OPerfModel", function(object) {
  model = object@model
  tmp = t(data.frame(model[-length(model)]))
  rownames(tmp) = c("AUC", "Gini", "Best Cutoff", "F1", "Accuracy", "Precision", "Recall", "Specificity", "Max per Class Error")
  colnames(tmp) = "Value"; print(tmp)
  cat("\n\nConfusion matrix:\n"); print(model$confusion)
})

#'
#' The H2OGLMModel class.
#'
#' This class represents a generalized linear model.
setClass("H2OGLMModel", representation(xval="list"), contains="H2OModel")

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

#'
#' The H2ODeepLearningModel class.
#'
#' This class represents a deep learning model.
setClass("H2ODeepLearningModel", representation(valid="H2OParsedData"), contains="H2OModel")

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

#'
#' The H2ODRFModel class.
#'
#' This class represents a distributed random forest model.
setClass("H2ODRFModel", representation(valid="H2OParsedData"), contains="H2OModel" )

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

#'
#' The H2OGBMModel class.
#'
#' This class represents a gradient boosted machines model.
setClass("H2OGBMModel", representation(valid="H2OParsedData"), contains="H2OModel")

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

#'
#' The H2OSpeeDRFModel class.
#'
#' This class represents a speedrf model. Another random forest model variant.
setClass("H2OSpeeDRFModel", representation(valid="H2OParsedData"), contains="H2OModel")

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

#'
#' The H2ONBModel class.
#'
#' This class represents a naive bayes model.
setClass("H2ONBModel", contains="H2OModel")

setMethod("show", "H2ONBModel", function(object) {
  print(object@data)
  cat("Naive Bayes Model Key:", object@key)

  model = object@model
  cat("\n\nA-priori probabilities:\n"); print(model$apriori_prob)
  cat("\n\nConditional probabilities:\n"); print(model$tables)
})

#'
#' The H2OPCAModel class.
#'
#' This class represents the results from a pricnipal components analysis.
setClass("H2OPCAModel", contains="H2OModel")

setMethod("show", "H2OPCAModel", function(object) {
  print(object@data)
  cat("PCA Model Key:", object@key)

  model = object@model
  cat("\n\nStandard deviations:\n", model$sdev)
  cat("\n\nRotation:\n"); print(model$rotation)
})

#'
#' The H2OKMeansModel class.
#'
#' This class represents the results of a KMeans model.
setClass("H2OKMeansModel", contains="H2OModel")

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

#'
#' The H2OGLMGrid class.
#'
#' The grid search for a generalized linear model.
setClass("H2OGLMGrid", contains="H2OGrid")

#'
#' The H2OGBMGrid class.
#'
#' The grid search for a gradient boosted machines model.
setClass("H2OGBMGrid", contains="H2OGrid")

#'
#' The H2OKMeansGrid class.
#'
#' The grid search for a KMeans model.
setClass("H2OKMeansGrid", contains="H2OGrid")

#'
#' The H2ODRFGrid class.
#'
#' The grid search for a distributed random forest model.
setClass("H2ODRFGrid", contains="H2OGrid")

#'
#' The H2ODeepLearningGrid class.
#'
#' The grid search for a deep learning model.
setClass("H2ODeepLearningGrid", contains="H2OGrid")


#-----------------------------------------------------------------------------------------------------------------------
# ValueArray Class Defintions
#-----------------------------------------------------------------------------------------------------------------------
#'
#' The H2ORawDataVA class.
#'
#' This class represents data in a post-import format.
#'
#' The H2ORawData is a representation of the imported, not yet parsed, data.
setClass("H2ORawDataVA", representation(h2o="H2OClient",  key="character"))

setMethod("show", "H2ORawDataVA", function(object) {
  print(object@h2o)
  cat("Raw Data Key:", object@key, "\n")
})

#'
#' The H2OParsedDataVA class.
#'
#' The H2OParsedDataVA class subclasses the H2OParsedData class.
setClass("H2OParsedDataVA", contains="H2OParsedData")

setMethod("show", "H2OParsedDataVA", function(object) {
  print(object@h2o)
  cat("Parsed Data Key:", object@key, "\n")
})

#'
#' The H2OModelVA class.
#'
#' A virtual class that refers to legacy ValueArray backed algorithms.
setClass("H2OModelVA", representation(key="character", data="H2OParsedDataVA", model="list", "VIRTUAL"))

#'
#' The H2OGridVA class.
#'
#' This virtual class represents a grid search performed by H2O over a legacy ValueArray backed algorithm.
#'
#' A grid search is an automated procedure for varying the parameters of a model and discovering the best tunings.
setClass("H2OGridVA", representation(key="character", data="H2OParsedDataVA", model="list", sumtable="list", "VIRTUAL"))

setMethod("show", "H2OGridVA", function(object) {
  print(object@data)
  cat("Grid Search Model Key:", object@key, "\n")

  temp = data.frame(t(sapply(object@sumtable, c)))
  cat("\nSummary\n"); print(temp)
})

#'
#' The H2OGLMModelVA class.
#'
#' The legacy ValueArray backed generalized linear model.
setClass("H2OGLMModelVA", representation(xval="list"), contains="H2OModelVA")

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

#'
#' The H2OGLMGridVA class.
#'
#' The legacy ValueArray backed grid search of a generalized linear model.
setClass("H2OGLMGridVA", contains="H2OGridVA")

#'
#' The H2OKMeansModelVA class.
#'
#' The legacy ValueArray backed KMeans model.
setClass("H2OKMeansModelVA", contains="H2OModelVA")

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

#'
#' The H2ORFModelVA class.
#'
#' The legacy ValueArray backed Random Forest model. It's directly analagous to the FluidVec backed SpeeDRF rather than DRF.
setClass("H2ORFModelVA", contains="H2OModelVA")

setMethod("show", "H2ORFModelVA", function(object) {
  print(object@data)
  cat("Random Forest Model Key:", object@key)

  model = object@model
  cat("\n\nClassification Error:", model$classification_error)
  cat("\nConfusion Matrix:\n"); print(model$confusion)
  cat("\nTree Stats:\n"); print(model$tree_sum)
})


#-----------------------------------------------------------------------------------------------------------------------
# AST Class Defintions
#-----------------------------------------------------------------------------------------------------------------------
#'
#' The Node class.
#'
#' An object of type Node inherits from an H2OFrame, but holds no H2O-aware data. Every node in the abstract syntax tree
#' has as its ancestor this class.
#'
#' Every node in the abstract syntax tree will have a symbol table, which is a dictionary of types and names for
#' all the relevant variables and functions defined in the current scope. A missing symbol is therefore discovered
#' by looking up the tree to the nearest symbol table defining that symbol.
setClass("Node", representation(node_type="character"), contains="H2OFrame")

#'
#' The ASTNode class.
#'
#' This class represents a node in the abstract syntax tree. An ASTNode has a root. The root has children that either
#' point to another ASTNode, or to a leaf node, which may be of type ASTNumeric or ASTFrame.
setClass("ASTNode", representation(root="Node", children="list"), contains="Node")

#'
#' The ASTOp class.
#'
#' This class represents an operator between one or more H2O objects. ASTOp nodes are always root nodes in a tree and
#' are never leaf nodes. Operators are discussed more in depth in ops.R.
setClass("ASTOp", representation(type="character", operator="character", infix="logical"), contains="Node",
         prototype(node_type = "ASTOp"))

#'
#' The ASTNumeric class.
#'
#' This class represents a numeric leaf node in the abstract syntax tree. This may either be a raw numerical value or a
#' variable containing a numerical value. In any case, the class of the object being placed is of type 'numeric'.
setClass("ASTNumeric", representation(type="character", value="numeric"), contains="Node",
         prototype(node_type = "ASTNumeric"))

#'
#' The ASTFrame class.
#'
#' This class represents a leaf containing an H2OFrame object.
setClass("ASTFrame", representation(type="character", value="character"), contains="Node",
         prototype(node_type = "ASTFrame"))

#'
#' The ASTUnk class.
#'
#' This class represents a leaf that
#' will be assigned to and has unkown type before evaluation (it's an 'unknown' identifier)
#' -OR-
#' it represents an operation on a function's argument if the symbol appears in the functions formals.
#' The distinction between these two cases is denoted by isFormal (TRUE -> Function arg, FALSE -> assignment)
#'
#' Note: Formal args are free variables.
setClass("ASTUnk", representation(key="character", isFormal="logical"), contains="Node",
         prototype(node_type = "ASTUnk"))

#'
#' The ASTString class.
#'
#' This class represents a leaf holding a string expression to be passed into some function.
setClass("ASTString", representation(type="character", value="character"), contains="Node",
         prototype(node_type = "ASTString"))

#'
#' The ASTFun class.
#'
#' This class represents a UDF.
setClass("ASTFun", representation(type="character", name="character", statements="list", arguments="vector"), contains="Node",
         prototype(node_type = "ASTFun"))

#'
#' The ASTArg class.
#'
#' This class represents an argument to a function.
setClass("ASTArg", representation(arg_name="character", arg_number="numeric", arg_value="ANY", arg_type="ANY"), contains="Node",
         prototype(node_type = "ASTArg"))

#'
#' The ASTSymbolTable Class.
#'
#' This class represents a symbol table. It is a table of free variables.
setClass("ASTSymbolTable", representation(symbols="list"), contains="Node",
         prototype(node_type = "ASTOp"))
