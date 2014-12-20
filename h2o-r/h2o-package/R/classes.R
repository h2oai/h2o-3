#'
#' Class definitions and their `show` & `summary` methods.
#'
#'
#' To conveniently and safely pass messages between R and H2O, this package relies
#' on S4 objects to capture and pass state. This R file contains all of the h2o
#' package's classes as well as their complementary `show` methods. The end user
#' will typically never have to reason with these objects directly, as there are
#' S3 accessor methods provided for creating new objects.
#'
#' @name ClassesIntro
NULL

#-----------------------------------------------------------------------------------------------------------------------
# Class Defintions
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
setClass("Node", contains="VIRTUAL")

#'
#' The ASTNode class.
#'
#' This class represents a node in the abstract syntax tree. An ASTNode has a root. The root has children that either
#' point to another ASTNode, or to a leaf node, which may be of type ASTNumeric or ASTFrame.
#' @slot root Object of type \code{Node}
#' @slot children Object of type \code{list}
setClass("ASTNode", representation(root="Node", children="list"), contains="Node")

#' @rdname ASTNode-class
setMethod("show", "ASTNode", function(object) cat(visitor(object)$ast, "\n") )

#'
#' The ASTApply class.
#'
#' This class represents an operator between one or more H2O objects. ASTApply nodes are always root nodes in a tree and
#' are never leaf nodes. Operators are discussed more in depth in ops.R.
setClass("ASTApply", representation(op="character"), contains="Node")

setClass("ASTEmpty",  representation(key="character"), contains="Node")
setClass("ASTBody",   representation(statements="list"), contains="Node")
setClass("ASTFun",    representation(name="character", arguments="character", body="ASTBody"), contains="Node")
setClass("ASTSpan",   representation(root="Node",    children  = "list"), contains="Node")
setClass("ASTSeries", representation(op="character", children  = "list"), contains="Node")
setClass("ASTIf",     representation(op="character", condition = "ASTNode",  body = "ASTBody"), contains="Node", prototype(op="if"))
setClass("ASTElse",   representation(op="character", body      = "ASTBody"), contains="Node", prototype(op="else"))
setClass("ASTFor",    representation(op="character", iterator  = "list",  body = "ASTBody"), contains="Node", prototype(op="for"))
setClass("ASTReturn", representation(op="character", children  = "ASTNode"), contains="Node", prototype(op="return"))

#'
#' The H2OConnection class.
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
#' @slot ip Object of class \code{character} representing the IP address of the H2O server.
#' @slot port Object of class \code{numeric} representing the port number of the H2O server.
#' @aliases H2OConnection
H2OConnection <- setClass("H2OConnection",
                      representation(ip="character", port="numeric"),
                      prototype(ip=NA_character_, port=NA_integer_)
                      )

#' @rdname H2OConnection-class
setMethod("show", "H2OConnection", function(object) {
  cat("IP Address:", object@ip,   "\n")
  cat("Port      :", object@port, "\n")
})

setClassUnion("H2OConnectionOrNULL", c("H2OConnection", "NULL"))
setClassUnion("ASTNodeOrNULL", c("ASTNode", "NULL"))
setClassUnion("data.frameOrNULL", c("data.frame", "NULL"))


#'
#' The H2OFrame class
#'
setClass("H2OFrame",
         representation(h2o="H2OConnectionOrNULL", key="character", ast="ASTNodeOrNULL",
         col_names="character", nrows="numeric", ncols="numeric", scalar="numeric",
         factors="data.frameOrNULL"),
         prototype(h2o       = NULL,
                   key       = NA_character_,
                   ast       = NULL,
                   col_names = NA_character_,
                   nrows     = NA_integer_,
                   ncols     = NA_integer_,
                   factors   = NULL,
                   scalar    = NA_integer_)
         )

setMethod("show", "H2OFrame", function(object) {
  print(object@h2o)
  cat("Key:", object@key, "\n")
  print(head(object))
  invisible(h2o.gc())
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
#' @slot h2o An \code{H2OConnection} object containing the IP address and port number of the H2O server.
#' @slot key An object of class \code{"character"}, which is the hex key assigned to the imported data.
#' @aliases H2ORawData
setClass("H2ORawData", representation(h2o="H2OConnection", key="character"))

#' @rdname H2ORawData-class
setMethod("show", "H2ORawData", function(object) {
  print(object@h2o)
  cat("Raw Data Key:", object@key, "\n")
})

# No show method for this type of object.

#'
#' The H2OW2V object.
#'
#' This class represents a h2o-word2vec object.
#'
setClass("H2OW2V", representation(h2o="H2OConnection", key="character", train.data="H2OFrame"))

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).

#' @slot h2o Object of class \code{H2OConnection}, which is the client object that was passed into the function call.
#' @slot key Object of class \code{character}, representing the unique hex key that identifies the model
#' @slot model Object of class \code{list} containing the characteristics of the model returned by the algorithm.
#' @slot raw_json Object of class \code{list} containing the raw JSON response
#' @aliases H2OModel
setClass("H2OModel", representation(h2o="H2OConnection", key="character", model="list", raw_json="list"), contains="VIRTUAL")

# No show method for this type of object.
#'
#' The H2OPerfModel class.
#'
#' This class represents the output of the evaluation of a binary classification model.
#'
#' @slot cutoffs A numeric vector of threshold values.
#' @slot measure A numeric vector of performance values corresponding to the threshold values. The specific performance measure is given in \code{perf}.
#' @slot perf A character string indicating the performance measure used to evaluate the model. One of either "F1", "Accuracy", "Error", "Precision", "Recall", "Specificity", "MCC", "Max per Class Error".
#' @slot model Object of class \code{list} containing the following elements:
#' \describe{
#'    \item{AUC}{Area under the curve.}
#'    \item{GINI}{Gini coefficient.}
#'    \item{Best Cutoff for}{Threshold value that optimizes the performance measure \code{perf}. If \code{perf} is "max_per_class_error", it is minimized at this threshold, otherwise, it is maximized.}
#'    \item{F1}{F1 score at best cutoff.}
#'    \item{Accuracy}{Accuracy value at best cutoff. Estimated as \eqn{(TP+TN)/(P+N)}.}
#'    \item{Precision}{Precision value at best cutoff. Estimated as \eqn{TP/(TP+FP)}.}
#'    \item{Recall}{Recall value at best cutoff, i.e. the true positive rate \eqn{TP/P}.}
#'    \item{Specificity}{Specificity value at best cutoff, i.e. the true negative rate \eqn{TN/N}.}
#'    \item{MCC}{Mathew's Correlation Coefficient}
#'    \item{Max per Class Error}{Maximum per class error at best cutoff.}
#'    \item{Confusion}{Confusion matrix at best cutoff.}
#' }
#' @slot roc A data frame with two columns: TPR = true positive rate and FPR = false positive rate, calculated at the listed cutoffs.
#' @aliases H2OPerfModel
setClass("H2OPerfModel", representation(cutoffs="numeric", measure="numeric", perf="character", model="list", roc="data.frame"))

#' @rdname H2OPerfModel-class
setMethod("show", "H2OPerfModel", function(object) {
  model <- object@model
  tmp <- t(data.frame(model[-length(model)]))

  if(object@perf == "mcc")
    criterion <- "MCC"
  else
    criterion <- paste(toupper(substring(object@perf, 1L, 1L)), substring(object@perf, 2L), sep = "")
  rownames(tmp) <- c("AUC", "Gini", paste("Best Cutoff for", criterion), "F1", "Accuracy", "Error",
                     "Precision", "Recall", "Specificity", "MCC", "Max per Class Error")
  colnames(tmp) <- "Value"; print(tmp)
  cat("\n\nConfusion matrix:\n"); print(model$confusion)
})

#'
#' The H2OGLMModel class.
#'
#' This class represents a generalized linear model.
#'
#' @slot xval List of objects of class \code{H2OGLMModel}, representing the n-fold cross-validation models.
#' @aliases H2OGLMModel
setClass("H2OGLMModel", representation(xval="list"), contains="H2OModel")

#' @rdname H2OGLMModel-class
setMethod("show", "H2OGLMModel", function(object) {
    print(object@data@h2o)
    cat("Parsed Data Key:", object@data@key, "\n\n")
    cat("GLM2 Model Key:", object@key)

    model <- object@model
    cat("\n\nCoefficients:\n"); print(round(model$coefficients,5L))
    if(!is.null(model$normalized_coefficients)) {
        cat("\nNormalized Coefficients:\n"); print(round(model$normalized_coefficients,5L))
    }
    cat("\nDegrees of Freedom:", model$df.null, "Total (i.e. Null); ", model$df.residual, "Residual")
    cat("\nNull Deviance:    ", round(model$null.deviance,1L))
    cat("\nResidual Deviance:", round(model$deviance,1L), " AIC:", round(model$aic,1L))
    cat("\nDeviance Explained:", round(1-model$deviance/model$null.deviance,5L), "\n")
    # cat("\nAvg Training Error Rate:", round(model$train.err,5L), "\n")

    family <- model$params$family$family
    if(family == "binomial") {
        cat("AUC:", round(model$auc,5L), " Best Threshold:", round(model$best_threshold,5L))
        cat("\n\nConfusion Matrix:\n"); print(model$confusion)
    }

    if(length(object@xval) > 0L) {
        cat("\nCross-Validation Models:\n")
        if(family == "binomial") {
            modelXval <- t(sapply(object@xval, function(x) { c(x@model$rank-1, x@model$auc, 1-x@model$deviance/x@model$null.deviance) }))
            colnames(modelXval) <- c("Nonzeros", "AUC", "Deviance Explained")
        } else {
            modelXval <- t(sapply(object@xval, function(x) { c(x@model$rank-1, x@model$aic, 1-x@model$deviance/x@model$null.deviance) }))
            colnames(modelXval) <- c("Nonzeros", "AIC", "Deviance Explained")
        }
        rownames(modelXval) <- paste("Model", seq_len(nrow(modelXval)))
        print(modelXval)
    }
})

#'
#' The H2OGLMModelList class.
#'
#' This class represents a list of generalized linear models produced from a lambda search.
#' @slot models Object of class \code{list} containing \code{H2OGLMModel} objects representing the models returned from the lambda search.
#' @slot best_model Object of class \code{numeric} indicating the index of the model with the optimal lambda value in the above list.
#' @slot lambdas Object of class \code{numeric} indicating the optimal lambda value from the lambda search.
#' @aliases H2OGLMModelList
setClass("H2OGLMModelList", representation(models="list", best_model="numeric", lambdas="numeric"))

#' @rdname H2OGLMModelList-class
setMethod("summary","H2OGLMModelList", function(object) {
    summary <- NULL
    if(object@models[[1L]]@model$params$family$family == 'binomial'){
        for(m in object@models) {
            model <- m@model
            if(is.null(summary)) {
                summary <- t(as.matrix(c(model$lambda, model$df.null-model$df.residual,round((1-model$deviance/model$null.deviance),2L),round(model$auc,2L))))
            } else {
                summary <- rbind(summary,c(model$lambda,model$df.null-model$df.residual,round((1-model$deviance/model$null.deviance),2L),round(model$auc,2L)))
            }
        }
        summary <- cbind(seq_len(nrow(summary)),summary)
        colnames(summary) <- c("id","lambda","predictors","dev.ratio"," AUC ")
    } else {
        for(m in object@models) {
            model <- m@model
            if(is.null(summary)) {
                summary <- t(as.matrix(c(model$lambda, model$df.null-model$df.residual,round((1-model$deviance/model$null.deviance),2L))))
            } else {
                summary <- rbind(summary,c(model$lambda,model$df.null-model$df.residual,round((1-model$deviance/model$null.deviance),2L)))
            }
        }
        summary <- cbind(seq_len(nrow(summary)),summary)
        colnames(summary) <- c("id","lambda","predictors","explained dev")
    }
    summary
})

#' @rdname H2OGLMModelList-class
setMethod("show", "H2OGLMModelList", function(object) {
    print(summary(object))
    cat("best model:",object@best_model, "\n")
})

#'
#' The H2ODeepLearningModel class.
#'
#' This class represents a deep learning model.
#' @slot valid Object of class \code{H2OFrame}, representing the validation data set.
#' @slot xval List of objects of class \code{H2ODeepLearningModel}, representing the n-fold cross-validation models.
#' @aliases H2ODeepLearningModel
setClass("H2ODeepLearningModel", representation(valid="H2OFrame", xval="list"), contains="H2OModel")

#' @rdname H2ODeepLearningModel-class
setMethod("show", "H2ODeepLearningModel", function(object) {
  cat("Deep Learning Model Key:", object@key)

  model <- object@model
  cat("\n\nTraining classification error:", model$train_class_error)
  cat("\nTraining mean square error:", model$train_sqr_error)
  cat("\n\nValidation classification error:", model$valid_class_error)
  cat("\nValidation square error:", model$valid_sqr_error)

  if(!is.null(model$confusion)) {
    cat("\n\nConfusion matrix:\n")
    if(is.na(object@valid@key)) {
      if(model$params$nfolds == 0L)
        cat("Reported on", object@data@key, "\n")
      else
        cat("Reported on ", model$params$nfolds, "-fold cross-validated data\n", sep="")
    } else
      cat("Reported on", object@valid@key, "\n")
    print(model$confusion)
  }

  if(!is.null(model$hit_ratios)) {
    cat("\nHit Ratios for Multi-class Classification:\n")
    print(model$hit_ratios)
  }

  if(!is.null(object@xval) && length(object@xval) > 0L) {
    cat("\nCross-Validation Models:\n")
    temp <- lapply(object@xval, function(x) cat(" ", x@key, "\n"))
  }
  cat("\n")
  cat("\nAvailable components:\n\n"); print(names(model))
})

#'
#' The H2ODRFModel class.
#'
#' This class represents a distributed random forest model.
#'
#' @slot valid Object of class \code{H2OFrame}, which is the data used for validating the model.
#' @slot xval List of objects of class \code{H2ODRFModel}, representing the n-fold cross-validation models.
#' @aliases H2ODRFModel
setClass("H2ODRFModel", representation(valid="H2OFrame", xval="list"), contains="H2OModel")

#' @rdname H2ODRFModel-class
setMethod("show", "H2ODRFModel", function(object) {
  print(object@data@h2o)
  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("Distributed Random Forest Model Key:", object@key)

  model = object@model
  cat("\n\nClassification:", model$params$classification)
  cat("\nNumber of trees:", model$params$ntree)
  cat("\nTree statistics:\n"); print(model$forest)

  if(model$params$classification) {
    cat("\nConfusion matrix:\n")
    if(is.na(object@valid@key))
      cat("Reported on ", object@model$params$nfolds, "-fold cross-validated data\n", sep="")
    else
      cat("Reported on", object@valid@key, "\n")
    print(model$confusion)

    if(!is.null(model$auc) && !is.null(model$gini))
      cat("\nAUC:", model$auc, "\nGini:", model$gini, "\n")
  }
  if(!is.null(model$varimp)) {
    cat("\nVariable importance:\n"); print(model$varimp)
  }
  cat("\nMean-squared Error by tree:\n"); print(model$mse)
  if(length(object@xval) > 0L) {
    cat("\nCross-Validation Models:\n")
    print(sapply(object@xval, function(x) x@key))
  }
})

#'
#' The H2OGBMModel class.
#'
#' This class represents a gradient boosted machines model.
#' @slot valid Object of class \code{\linkS4class{H2OFrame}}, which is the dataset used to validate the model.
#' @slot xval List of objects of class \code{H2OGBMModel}, representing the n-fold cross-validation models.
#' @aliases H2OGBMModel
setClass("H2OGBMModel", representation(valid="H2OFrame", xval="list"), contains="H2OModel")

#' @rdname H2OGBMModel-class
setMethod("show", "H2OGBMModel", function(object) {
# print(object@data@h2o)
#  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("GBM Model Key:", object@key, "\n")

#  model = object@model
#  if(model$params$distribution %in% c("multinomial", "bernoulli")) {
#    cat("\nConfusion matrix:\n")
#    if(is.na(object@valid@key))
#      cat("Reported on", paste(object@model$params$nfolds, "-fold cross-validated data", sep = ""), "\n")
#    else
#      cat("Reported on", object@valid@key, "\n")
#    print(model$confusion)
#
#    if(!is.null(model$auc) && !is.null(model$gini))
#      cat("\nAUC:", model$auc, "\nGini:", model$gini, "\n")
#  }

#  if(!is.null(model$varimp)) {
#    cat("\nVariable importance:\n"); print(model$varimp)
#  }
#  cat("\nMean-squared Error by tree:\n"); print(model$err)
#  if(length(object@xval) > 0) {
#    cat("\nCross-Validation Models:\n")
#    print(sapply(object@xval, function(x) x@key))
#  }
})

#'
#' The H2OQuantileModel class.
#'
#' This class represents a quantile model.
#' @slot valid Object of class \code{\linkS4class{H2OFrame}}, which is the dataset used to build the model.
#' @aliases H2OQuantileModel
setClass("H2OQuantileModel", representation(valid="H2OFrame", xval="list"), contains="H2OModel")

#' @rdname H2OQuantileModel-class
setMethod("show", "H2OQuantileModel", function(object) {
# print(object@data@h2o)
#  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("Quantile Model Key:", object@key, "\n")
})

#'
#' The H2OSpeeDRFModel class.
#'
#' This class represents a speedrf model. Another random forest model variant.
#' @slot valid Object of class \code{H2OFrame}, which is the data used for validating the model.
#' @slot list List of objects of class \code{H2OSpeeDRFModel}, representing the n-fold cross-validation models.
#' @aliases H2OSpeeDRFModel
setClass("H2OSpeeDRFModel", representation(valid="H2OFrame", xval="list"), contains="H2OModel")

#' @rdname H2OSpeeDRFModel-class
setMethod("show", "H2OSpeeDRFModel", function(object) {
  print(object@data@h2o)
  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("Random Forest Model Key:", object@key)
  cat("\n\nSeed Used: ", object@model$params$seed)

  model <- object@model
  cat("\n\nClassification:", model$params$classification)
  cat("\nNumber of trees:", model$params$ntree)

  if(FALSE){ #model$params$oobee) {
    cat("\nConfusion matrix:\n"); cat("Reported on oobee from", object@valid@key, "\n")
    if(is.na(object@valid@key))
      cat("Reported on oobee from ", object@model$params$nfolds, "-fold cross-validated data\n", sep="")
    else
      cat("Reported on oobee from", object@valid@key, "\n")
  } else {
    cat("\nConfusion matrix:\n");
    if(is.na(object@valid@key))
      cat("Reported on ", object@model$params$nfolds, "-fold cross-validated data\n", sep="")
    else
      cat("Reported on", object@valid@key, "\n")
  }
  print(model$confusion)

  if(!is.null(model$varimp)) {
    cat("\nVariable importance:\n"); print(model$varimp)
  }

  #mse <-model$mse[length(model$mse)] # (model$mse[is.na(model$mse) | model$mse <= 0] <- "")

  if (model$mse != -1) {
    cat("\nMean-squared Error from the",model$params$ntree, "trees: "); cat(model$mse, "\n")
  }

  if(length(object@xval) > 0L) {
    cat("\nCross-Validation Models:\n")
    print(sapply(object@xval, function(x) x@key))
  }
})

#'
#' The H2ONBModel class.
#'
#' This class represents a naive bayes model.
#' @aliases H2ONBModel
setClass("H2ONBModel", contains="H2OModel")

#' @rdname H2ONBModel-class
setMethod("show", "H2ONBModel", function(object) {
  print(object@data@h2o)
  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("Naive Bayes Model Key:", object@key)

  model <- object@model
  cat("\n\nA-priori probabilities:\n"); print(model$apriori_prob)
  cat("\n\nConditional probabilities:\n"); print(model$tables)
})


#'
#' The H2OPCAModel class.
#'
#' This class represents the results from a pricnipal components analysis.
#' @aliases H2OPCAModel
setClass("H2OPCAModel", contains="H2OModel")

#' @rdname H2OPCAModel-class
setMethod("show", "H2OPCAModel", function(object) {
  print(object@data@h2o)
  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("PCA Model Key:", object@key)

  model <- object@model
  cat("\n\nStandard deviations:\n", model$sdev)
  cat("\n\nRotation:\n"); print(model$rotation)
})

#'
#' The H2OKMeansModel class.
#'
#' This class represents the results of a KMeans model.
#' @aliases H2OKMeansModel
setClass("H2OKMeansModel", representation(valid="H2OFrame", xval="list"), contains="H2OModel")

#' @rdname H2OKMeansModel-class
setMethod("show", "H2OKMeansModel", function(object) {
#    cat("Parsed Data Key:", object@data@key, "\n\n")
    cat("K-means Model Key:", object@key)

    model <- object@model
    cat("\n\nK-means clustering with", length(model$rows), "clusters of sizes "); cat(model$rows, sep=", ")
    cat("\n\nCluster means:\n"); print(model$clusters)
    cat("\nWithin cluster mean squared error by cluster:\n"); print(model$withinmse)
    cat("(between_SS / total_SS = ", round(100*model$avgbetweenss/model$avgss, 2L), "%)\n")
#    cat("\n\nCluster means:\n"); print(model$centers)
#    cat("\nClustering vector:\n"); print(summary(model$clusters))
#    cat("\nWithin cluster sum of squares by cluster:\n"); print(model$withinss)
#    cat("(between_SS / total_SS = ", round(100*sum(model$betweenss)/model$totss, 1), "%)\n")
    cat("\nAvailable components:\n\n"); print(names(model))
})


#'
#' The H2OGrid class.
#'
#' This virtual class represents a grid search performed by H2O.
#'
#' A grid search is an automated procedure for varying the parameters of a model and discovering the best tunings.
#' @slot keys Object of class \code{character}, representing the unique hex key that identifies the model.
#' @slot data Object of class \code{H2OFrame}, which is the input data used to build the model.
#' @slot model Object of class \code{list} containing \code{H2OModel} objects representing the models returned by the grid search algorithm.
#' @slot sumtable Object of class \code{list} containing summary statistics of all the models returned by the grid search algorithm.
#' @aliases H2OGrid
setClass("H2OGrid", representation(key="character",   data="H2OFrame", model="list", sumtable="list", "VIRTUAL"))

#' @rdname H2OGrid-class
setMethod("show", "H2OGrid", function(object) {
  print(object@data@h2o)
  cat("Parsed Data Key:", object@data@key, "\n\n")
  cat("Grid Search Model Key:", object@key, "\n")

  temp <- data.frame(t(sapply(object@sumtable, c)))
  cat("\nSummary\n"); print(temp)
})

#'
#' The H2OGLMGrid class.
#'
#' The grid search for a generalized linear model.
#' @aliases H2OGLMGrid
setClass("H2OGLMGrid", contains="H2OGrid")

#'
#' The H2OGBMGrid class.
#'
#' The grid search for a gradient boosted machines model.
#' @aliases H2OGBMGrid
setClass("H2OGBMGrid", contains="H2OGrid")

#'
#' The H2OKMeansGrid class.
#'
#' The grid search for a KMeans model.
#' @aliases H2OKMeansGrid
setClass("H2OKMeansGrid", contains="H2OGrid")

#'
#' The H2ODRFGrid class.
#'
#' The grid search for a distributed random forest model.
#' @aliases H2ODRFGrid
setClass("H2ODRFGrid", contains="H2OGrid")

#'
#' The H2ODeepLearningGrid class.
#'
#' The grid search for a deep learning model.
#' @aliases H2ODeepLearningGrid
setClass("H2ODeepLearningGrid", contains="H2OGrid")

#'
#' The H2OSpeeDRFGrid class.
#'
#' The grid search object for a speedrf model.
#' @aliases H2OSpeeDRFGrid
setClass("H2OSpeeDRFGrid", contains="H2OGrid")

#-----------------------------------------------------------------------------------------------------------------------
# Class Utils
#-----------------------------------------------------------------------------------------------------------------------

.isH2O <- function(x) { is(x, "H2OFrame") || is(x, "H2OConnection") || is(x, "H2ORawData") }
.retrieveH2O<-
function(env) {
  e_list <- unlist(lapply(ls(env), function(x) {
    tryCatch(is(get(x, envir=env), "H2OConnection"), error = function(e) FALSE)
             }))
  if (any(e_list)) {
    if (sum(e_list) > 1L) {
      x <- e_list[1L]
      for (y in e_list[1L])
        if (!identical(x, y)) stop("Found multiple h2o client connectors. Please specify the preferred h2o connection.")
    }
    return(get(ls(env)[which(e_list)[1L]], envir=env))
  }
  g_list <- unlist(lapply(ls(globalenv()), function(x) is(get(x, envir=globalenv()), "H2OConnection")))
  if (any(g_list)) {
    if (sum(g_list) > 1L) {
      x <- g_list[1L]
      for (y in g_list[1L])
        if (!identical(x, y)) stop("Found multiple h2o client connectors. Please specify the preferred h2o connection.")
    }
    return(get(ls(globalenv())[which(g_list)[1L]], envir=globalenv()))
  }
  stop("Could not find any H2OConnection. Do you have an active connection to H2O from R? Please specify the h2o connection.")
}
