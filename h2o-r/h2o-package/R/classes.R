#`
#` Class definitions and their `show` & `summary` methods.
#`
#`
#` To conveniently and safely pass messages between R and H2O, this package relies
#` on S4 objects to capture and pass state. This R file contains all of the h2o
#` package's classes as well as their complementary `show` methods. The end user
#` will typically never have to reason with these objects directly, as there are
#` S3 accessor methods provided for creating new objects.
#`

#-----------------------------------------------------------------------------------------------------------------------
# Class Definitions
#-----------------------------------------------------------------------------------------------------------------------

setClassUnion("data.frameOrNULL", c("data.frame", "NULL"))
setClassUnion("listOrNull", c("list", "NULL"))

if (inherits(try(getRefClass("H2OConnectionMutableState"), silent = TRUE), "try-error")) {
# TODO: Address issue below
# H2O.ai testing infrastructure sources .R files in addition to loading the h2o package
# avoid redefinition of reference class

#'
#' The H2OConnectionMutableState class
#'
#' This class represents the mutable aspects of a connection to an H2O cloud.
#'
#' @name H2OConnectionMutableState
#' @slot session_id A \code{character} string specifying the H2O session identifier.
#' @slot key_count A \code{integer} value specifying count for the number of keys generated for the \code{session_id}.
#' @aliases H2OConnectionMutableState
setRefClass("H2OConnectionMutableState",
            fields = list(session_id = "character", key_count = "integer"),
            methods = list(
              initialize =
              function(..., session_id = NA_character_, key_count = 0L) {
                .self$initFields(session_id = session_id, key_count = key_count)
                callSuper(...)
              }))
}

#'
#' The H2OConnection class.
#'
#' This class represents a connection to an H2O cloud.
#'
#' Because H2O is not a master-slave architecture, there is no restriction on which H2O node
#' is used to establish the connection between R (the client) and H2O (the server).
#'
#' A new H2O connection is established via the h2o.init() function, which takes as parameters
#' the `ip` and `port` of the machine running an instance to connect with. The default behavior
#' is to connect with a local instance of H2O at port 54321, or to boot a new local instance if one
#' is not found at port 54321.
#' @slot ip A \code{character} string specifying the IP address of the H2O cloud.
#' @slot port A \code{numeric} value specifying the port number of the H2O cloud.
#' @slot proxy A \code{character} specifying the proxy path of the H2O cloud.
#' @slot https Set this to TRUE to use https instead of http.
#' @slot insecure Set this to TRUE to disable SSL certificate checking.
#' @slot username Username to login with.
#' @slot password Password to login with.
#' @slot cookies Cookies to add to request
#' @slot context_path Context path which is appended to H2O server location.
#' @slot mutable An \code{H2OConnectionMutableState} object to hold the mutable state for the H2O connection.
#' @aliases H2OConnection
#' @export
setClass("H2OConnection",
         representation(ip="character", port="numeric", proxy="character",
                        https="logical", insecure="logical",
                        username="character", password="character",
                        cookies="character",
                        context_path="character",
                        mutable="H2OConnectionMutableState"),
         prototype(ip           = NA_character_,
                   port         = NA_integer_,
                   proxy        = NA_character_,
                   https        = FALSE,
                   insecure     = FALSE,
                   username     = NA_character_,
                   password     = NA_character_,
                   cookies      = NA_character_,
                   context_path = NA_character_,
                   mutable      = new("H2OConnectionMutableState")))

setClassUnion("H2OConnectionOrNULL", c("H2OConnection", "NULL"))

#' @rdname H2OConnection-class
#' @param object an \code{H2OConnection} object.
#' @export
setMethod("show", "H2OConnection", function(object) {
  cat("IP Address:", object@ip,                 "\n")
  cat("Port      :", object@port,               "\n")
  cat("Session ID:", object@mutable$session_id, "\n")
  cat("Key Count :", object@mutable$key_count,  "\n")
})

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot model_id A \code{character} string specifying the key for the model fit in the H2O cloud's key-value store.
#' @slot algorithm A \code{character} string specifying the algorithm that were used to fit the model.
#' @slot parameters A \code{list} containing the parameter settings that were used to fit the model that differ from the defaults.
#' @slot allparameters A \code{list} containg all parameters used to fit the model.
#' @slot have_pojo A \code{logical} indicating whether export to POJO is supported
#' @slot have_mojo A \code{logical} indicating whether export to MOJO is supported
#' @slot model A \code{list} containing the characteristics of the model returned by the algorithm.
#' @aliases H2OModel
#' @export
setClass("H2OModel",
         representation(model_id="character", algorithm="character", parameters="list", allparameters="list", have_pojo="logical", have_mojo="logical", model="list"),
         prototype(model_id=NA_character_),
         contains="VIRTUAL")

# TODO: make a more model-specific constructor
.newH2OModel <- function(Class, model_id, ...) {
  new(Class, model_id=model_id, ...)
}

#' @rdname H2OModel-class
#' @param object an \code{H2OModel} object.
#' @export
setMethod("show", "H2OModel", function(object) {
  o <- object
  model.parts <- .model.parts(o)
  m <- model.parts$m
  cat("Model Details:\n")
  cat("==============\n\n")
  cat(class(o), ": ", o@algorithm, "\n", sep = "")
  cat("Model ID: ", o@model_id, "\n")

  # summary
  print(m$model_summary)

  # if glm, print the coefficeints
  cat("\n")
  if( !is.null(m$coefficients_table) ) print(m$coefficients_table)

  # metrics
  cat("\n")
  if( !is.null(model.parts$tm) ) print(model.parts$tm)
  cat("\n")
  if( !is.null(model.parts$vm) ) print(model.parts$vm)
  cat("\n")
  if( !is.null(model.parts$xm) ) print(model.parts$xm)
  cat("\n")
  if( !is.null(model.parts$xms) ) print(model.parts$xms)
})

#'
#' Print the Model Summary
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param ... further arguments to be passed on (currently unimplemented)
#' @export
setMethod("summary", "H2OModel", function(object, ...) {
  o <- object
  model.parts <- .model.parts(o)
  m <- model.parts$m
  cat("Model Details:\n")
  cat("==============\n\n")
  cat(class(o), ": ", o@algorithm, "\n", sep = "")
  cat("Model Key: ", o@model_id, "\n")

  # summary
  print(m$model_summary)

  # metrics
  cat("\n")
  if( !is.null(model.parts$tm) ) print(model.parts$tm)
  cat("\n")
  if( !is.null(model.parts$vm) ) print(model.parts$vm)
  cat("\n")
  if( !is.null(model.parts$xm) ) print(model.parts$xm)
  cat("\n")
  if( !is.null(model.parts$xms) ) print(model.parts$xms)

  # History
  cat("\n")
  print(h2o.scoreHistory(o))

  # Varimp
  cat("\n")

  # VI could be real, true variable importances or GLM coefficients
  haz_varimp <- !is.null(m$variable_importances) || !is.null(m$standardized_coefficient_magnitudes)
  if( haz_varimp ) {
    cat("Variable Importances: (Extract with `h2o.varimp`) \n")
    cat("=================================================\n\n")
    print(h2o.varimp(o))
  }
})

.showMultiMetrics <- function(o, which="Training") {
  arg <- "train"
  haveModel <- !is.null(which)
  if (haveModel) {
    if( which == "Validation" ) { arg <- "valid" }
    else if ( which == "Cross-Validation" ) { arg <- "xval" }
    else if ( which == "Test" ) { arg <- "test" }
  }
  tm <- o@metrics
  if (haveModel) {
    cat(which, "Set Metrics: \n")
    cat("=====================\n")
  }
  if (arg != "test") {
    if( !is.null(tm[["frame"]]) && !is.null(tm[["frame"]][["name"]]) )  cat("\nExtract", tolower(which),"frame with", paste0("`h2o.getFrame(\"",tm$frame$name, "\")`"))
  }
  if( !is.null(tm$MSE)                                             )  cat("\nMSE: (Extract with `h2o.mse`)", tm$MSE)
  if( !is.null(tm$RMSE)                                             )  cat("\nRMSE: (Extract with `h2o.rmse`)", tm$RMSE)
  if( !is.null(tm$mae)                                             )  cat("\nMAE: (Extract with `h2o.mae`)", tm$mae)
  if( !is.null(tm$rmsle)                                             )  cat("\nRMSLE: (Extract with `h2o.rmsle`)", tm$rmsle)
  if( !is.null(tm$logloss)                                         )  cat("\nLogloss: (Extract with `h2o.logloss`)", tm$logloss)
  if( !is.null(tm$mean_per_class_error)                            )  cat("\nMean Per-Class Error:", tm$mean_per_class_error)
  if( !is.null(tm$AUC)                                             )  cat("\nAUC: (Extract with `h2o.auc`)", tm$AUC)
  if( !is.null(tm$Gini)                                            )  cat("\nGini: (Extract with `h2o.gini`)", tm$Gini)
  if( !is.null(tm$null_deviance)                                   )  cat("\nNull Deviance: (Extract with `h2o.nulldeviance`)", tm$null_deviance)
  if( !is.null(tm$residual_deviance)                               )  cat("\nResidual Deviance: (Extract with `h2o.residual_deviance`)", tm$residual_deviance)
  if(exists(o@algorithm) && o@algorithm == "glm") {
    if( !is.null(tm$r2) && !is.na(tm$r2)                           )  cat("\nR^2: (Extract with `h2o.r2`)", tm$r2)
  }
  if( !is.null(tm$AIC)                                             )  cat("\nAIC: (Extract with `h2o.aic`)", tm$AIC)
  if (arg != "test") {
    if( !is.null(tm$cm)                                              )  { if ( arg != "xval" ) { cat(paste0("\nConfusion Matrix: Extract with `h2o.confusionMatrix(<model>,", arg, " = TRUE)`)\n")); } }
  } else {
    if( !is.null(tm$cm)                                              )  { if ( arg != "xval" ) { cat(paste0("\nConfusion Matrix: Extract with `h2o.confusionMatrix(<model>, <data>)`)\n")); } }
  }
  if( !is.null(tm$cm)                                              )  { if ( arg != "xval" ) { cat("=========================================================================\n"); print(tm$cm$table) } }
  if (arg != "test") {
    if( !is.null(tm$hit_ratio_table)                                 )  cat(paste0("\nHit Ratio Table: Extract with `h2o.hit_ratio_table(<model>,", arg, " = TRUE)`\n"))
  } else {
    if( !is.null(tm$hit_ratio_table)                                 )  cat(paste0("\nHit Ratio Table: Extract with `h2o.hit_ratio_table(<model>, <data>)`\n"))
  }
  if( !is.null(tm$hit_ratio_table)                                 )  { cat("=======================================================================\n"); print(h2o.hit_ratio_table(tm$hit_ratio_table)); }
  cat("\n")
  invisible(tm)
}

#' @rdname H2OModel-class
#' @export
setClass("H2OUnknownModel",     contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OBinomialModel",    contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OMultinomialModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OOrdinalModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2ORegressionModel",  contains="H2OModel")
#'
#' The H2OClusteringModel object.
#'
#' This virtual class represents a clustering model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot model_id A \code{character} string specifying the key for the model fit in the H2O cloud's key-value store.
#' @slot algorithm A \code{character} string specifying the algorithm that was used to fit the model.
#' @slot parameters A \code{list} containing the parameter settings that were used to fit the model that differ from the defaults.
#' @slot allparameters A \code{list} containing all parameters used to fit the model.
#' @slot model A \code{list} containing the characteristics of the model returned by the algorithm.
#'        \describe{
#'          \item{size }{The number of points in each cluster.}
#'          \item{totss }{Total sum of squared error to grand mean.}
#'          \item{withinss }{A vector of within-cluster sum of squared error.}
#'          \item{tot_withinss }{Total within-cluster sum of squared error.}
#'          \item{betweenss }{Between-cluster sum of squared error.}
#'        }
#' @export
setClass("H2OClusteringModel",  contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OAutoEncoderModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2ODimReductionModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OWordEmbeddingModel", contains="H2OModel")

#'
#' The H2OCoxPHModel object.
#'
#' Virtual object representing H2O's CoxPH Model.
#'
#' @aliases H2OCoxPHModel
#' @export
setClass("H2OCoxPHModel", contains="H2OModel")

#'
#' The H2OCoxPHModelSummary object.
#'
#' Wrapper object for summary information compatible with survival package.
#'
#' @slot summary A \code{list} containing the a summary compatible with CoxPH summary used in the survival package.
#' @aliases H2OCoxPHModelSummary
#' @export
setClass("H2OCoxPHModelSummary", representation(summary="list"))

#' @rdname H2OCoxPHModel-class
#' @param object an \code{H2OCoxPHModel} object.
#' @export
setMethod("show", "H2OCoxPHModel", function(object) {
    o <- object
    model.parts <- .model.parts(o)
    m <- model.parts$m
    cat("Model Details:\n")
    cat("==============\n\n")
    cat(class(o), ": ", o@algorithm, "\n", sep = "")
    cat("Model ID: ", o@model_id, "\n")

    # summary
    get("print.coxph", getNamespace("survival"))(.as.survival.coxph.model(o@model))
})

#'
#' Print the CoxPH Model Summary
#'
#' @param object An \linkS4class{H2OCoxPHModelSummary} object.
#' @param ... further arguments to be passed on (currently unimplemented)
#' @export
setMethod("show", "H2OCoxPHModelSummary", function(object)
  get("print.summary.coxph", getNamespace("survival"))(object@summary))

#'
#' Print the CoxPH Model Summary
#'
#' @param object an \code{H2OCoxPHModel} object.
#' @param conf.int a specification of the confidence interval.
#' @param scale a scale.
#' @export
setMethod("summary", "H2OCoxPHModel",
          function(object, conf.int = 0.95, scale = 1) {
            res <- .as.survival.coxph.summary(object@model)
            if (conf.int == 0)
              res@summary$conf.int <- NULL
            else {
              z <- stats::qnorm((1 + conf.int)/2, 0, 1)
              coef <- scale * res@summary$coefficients[,    "coef",  drop = TRUE]
              se   <- scale * res@summary$coefficients[, "se(coef)", drop = TRUE]
              shift <- z * se
              res@summary$conf.int <-
                structure(cbind(exp(coef), exp(- coef), exp(coef - shift), exp(coef + shift)),
                          dimnames =
                            list(rownames(res@summary$coefficients),
                                 c("exp(coef)", "exp(-coef)",
                                   sprintf("lower .%.0f", 100 * conf.int),
                                   sprintf("upper .%.0f", 100 * conf.int))))
            }
            res
          })

coef.H2OCoxPHModel        <- function(object, ...) .as.survival.coxph.model(object@model)$coefficients
coef.H2OCoxPHModelSummary <- function(object, ...) object@summary$coefficients

extractAIC.H2OCoxPHModel <- function(fit, scale, k = 2, ...) {
  fun <- get("extractAIC.coxph", getNamespace("stats"))
  if (missing(scale))
    fun(.as.survival.coxph.model(fit@model), k = k)
  else
    fun(.as.survival.coxph.model(fit@model), scale = scale, k = k)
}

logLik.H2OCoxPHModel <- function(object, ...)
  get("logLik.coxph", getNamespace("survival"))(.as.survival.coxph.model(object@model), ...)

vcov.H2OCoxPHModel <- function(object, ...)
  get("vcov.coxph", getNamespace("survival"))(.as.survival.coxph.model(object@model), ...)

#'
#' Accessor Methods for H2OModel Object
#'
#' Function accessor methods for various H2O output fields.
#'
#' @param object an \linkS4class{H2OModel} class object.
#' @name ModelAccessors
NULL

#' @rdname ModelAccessors
#' @export
setGeneric("getParms", function(object) { standardGeneric("getParms") })
#' @rdname ModelAccessors
#' @export
setMethod("getParms", "H2OModel", function(object) { object@parameters })

#' @rdname ModelAccessors
#' @export
setGeneric("getCenters", function(object) { standardGeneric("getCenters") })
#' @rdname ModelAccessors
#' @export
setGeneric("getCentersStd", function(object) { standardGeneric("getCentersStd") })
#' @rdname ModelAccessors
#' @export
setGeneric("getWithinSS", function(object) { standardGeneric("getWithinSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getTotWithinSS", function(object) { standardGeneric("getTotWithinSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getBetweenSS", function(object) { standardGeneric("getBetweenSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getTotSS", function(object) { standardGeneric("getTotSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getIterations", function(object) { standardGeneric("getIterations") })
#' @rdname ModelAccessors
#' @export
setGeneric("getClusterSizes", function(object) { standardGeneric("getClusterSizes") })

#' @rdname ModelAccessors
#' @export
setMethod("getCenters", "H2OClusteringModel", function(object) { as.data.frame(object@model$centers)[,-1] })
#' @rdname ModelAccessors
#' @export
setMethod("getCentersStd", "H2OClusteringModel", function(object) { as.data.frame(object@model$centers_std)[,-1] })
#' @rdname ModelAccessors
#' @export
setMethod("getWithinSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$centroid_stats$within_cluster_sum_of_squares })
#' @rdname ModelAccessors
#' @export
setMethod("getTotWithinSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$tot_withinss })
#' @rdname ModelAccessors
#' @export
setMethod("getBetweenSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$betweenss })
#' @rdname ModelAccessors
#' @export
setMethod("getTotSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$totss } )
#' @rdname ModelAccessors
#' @export
setMethod("getIterations", "H2OClusteringModel", function(object) { object@model$model_summary$number_of_iterations })
#' @rdname ModelAccessors
#' @export
setMethod("getClusterSizes", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$centroid_stats$size })

#'
#' The H2OModelMetrics Object.
#'
#' A class for constructing performance measures of H2O models.
#'
#' @aliases H2OModelMetrics
#' @export
setClass("H2OModelMetrics",
         representation(algorithm="character", on_train="logical", on_valid="logical", on_xval="logical", metrics="listOrNull"),
         prototype(algorithm=NA_character_, on_train=FALSE, on_valid=FALSE, on_xval=FALSE, metrics=NULL),
         contains="VIRTUAL")

#' @rdname H2OModelMetrics-class
#' @param object An \code{H2OModelMetrics} object
#' @export
setMethod("show", "H2OModelMetrics", function(object) {
    cat(class(object), ": ", object@algorithm, "\n", sep="")
    if( object@on_train & object@algorithm != "pca" ) cat("** Reported on training data. **\n")
    if( object@on_valid & object@algorithm != "pca" ) cat("** Reported on validation data. **\n")
    if( object@on_xval & object@algorithm != "pca"  ) cat("** Reported on cross-validation data. **\n")
    if( !is.null(object@metrics$description) ) cat("** ", object@metrics$description, " **\n\n", sep="")
    else                                       cat("\n")
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OUnknownMetrics",     contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OBinomialMetrics",    contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OBinomialMetrics", function(object) {
    callNextMethod(object)  # call to the super
    cat("MSE:  ", object@metrics$MSE, "\n", sep="")
    cat("RMSE:  ", object@metrics$RMSE, "\n", sep="")
    cat("LogLoss:  ", object@metrics$logloss, "\n", sep="")
    cat("Mean Per-Class Error:  ", object@metrics$mean_per_class_error, "\n", sep="")
    cat("AUC:  ", object@metrics$AUC, "\n", sep="")
    cat("Gini:  ", object@metrics$Gini, "\n", sep="")
    if(exists(object@algorithm) && object@algorithm == "glm") {

      if (!is.null(object@metrics$r2) && !is.na(object@metrics$r2)) cat("R^2:  ", object@metrics$r2, "\n", sep="")
      if (!is.null(object@metrics$null_deviance0)) cat("Null Deviance:  ", object@metrics$null_deviance,"\n", sep="")
      if (!is.null(object@metrics$residual_deviance)) cat("Residual Deviance:  ", object@metrics$residual_deviance,"\n", sep="")
      if (!is.null(object@metrics$AIC)) cat("AIC:  ", object@metrics$AIC,"\n", sep="")
    }
    cat("\n")
    cm <- h2o.confusionMatrix(object)
    if( is.null(cm) ) print(NULL)
    else {
      attr(cm, "header") <- "Confusion Matrix (vertical: actual; across: predicted) for F1-optimal threshold"
      print(cm)
      cat("\n")
    }
    print(object@metrics$max_criteria_and_metric_scores)

    desc <- object@metrics$description
    ## for user-given actual/predicted, show the gains/lift table, and don't show the 'Extract' message
    if (!is.null(desc) && regexpr('user-given', desc)!=-1) {
      cat("\n")
      if (!is.null(object@metrics$gains_lift_table)) {
        print(object@metrics$gains_lift_table)
      }
    } else {
      cat("\nGains/Lift Table: Extract with `h2o.gainsLift(<model>, <data>)` or `h2o.gainsLift(<model>, valid=<T/F>, xval=<T/F>)`")
    }
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OMultinomialMetrics", contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OMultinomialMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)  # call super
    if( object@on_train ) .showMultiMetrics(object, "Training")
    else if( object@on_valid ) .showMultiMetrics(object, "Validation")
    else if( object@on_xval ) .showMultiMetrics(object, "Cross-Validation")
    else if( !is.null(object@metrics$frame$name) ) .showMultiMetrics(object, "Test")
    else .showMultiMetrics(object, NULL)
  } else print(NULL)
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OOrdinalMetrics", contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OOrdinalMetrics", function(object) {
    if( !is.null(object@metrics) ) {
        callNextMethod(object)  # call super
        if( object@on_train ) .showMultiMetrics(object, "Training")
        else if( object@on_valid ) .showMultiMetrics(object, "Validation")
        else if( object@on_xval ) .showMultiMetrics(object, "Cross-Validation")
        else if( !is.null(object@metrics$frame$name) ) .showMultiMetrics(object, "Test")
        else .showMultiMetrics(object, NULL)
    } else print(NULL)
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2ORegressionMetrics",  contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2ORegressionMetrics", function(object) {
  callNextMethod(object)
  cat("MSE:  ", object@metrics$MSE, "\n", sep="")
  cat("RMSE:  ", object@metrics$RMSE, "\n", sep="")
  cat("MAE:  ", object@metrics$mae, "\n", sep="")
  cat("RMSLE:  ", object@metrics$rmsle, "\n", sep="")
  cat("Mean Residual Deviance :  ", h2o.mean_residual_deviance(object), "\n", sep="")
  if(exists(object@algorithm) && object@algorithm == "glm") {
    if (!is.na(h2o.r2(object))) cat("R^2 :  ", h2o.r2(object), "\n", sep="")
    null_dev <- h2o.null_deviance(object)
    res_dev  <- h2o.residual_deviance(object)
    null_dof <- h2o.null_dof(object)
    res_dof  <- h2o.residual_dof(object)
    aic      <- h2o.aic(object)
    if( !is.null(null_dev) ) cat("Null Deviance :", null_dev, "\n", sep="")
    if( !is.null(null_dof) ) cat("Null D.o.F. :",   null_dof, "\n", sep="")
    if( !is.null(res_dev ) ) cat("Residual Deviance :", res_dev, "\n", sep="")
    if( !is.null(res_dof ) ) cat("Residual D.o.F. :",   res_dof, "\n", sep="")
    if( !is.null(aic     ) ) cat("AIC :", aic, "\n", sep="")
  }
  cat("\n")
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OClusteringMetrics",  contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OClusteringMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)
    m <- object@metrics
    cat("\nTotal Within SS: ", m$tot_withinss)
    cat("\nBetween SS: ", m$betweenss)
    cat("\nTotal SS: ", m$totss, "\n")
    print(m$centroid_stats)
  } else print(NULL)
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OAutoEncoderMetrics", contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OAutoEncoderMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)  # call super
    object@metrics$frame$name <- NULL
    if( object@on_train ) .showMultiMetrics(object, "Training")
    if( object@on_valid ) .showMultiMetrics(object, "Validation")
    if( object@on_xval ) .showMultiMetrics(object, "Cross-Validation")
  } else print(NULL)
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2ODimReductionMetrics", contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2ODimReductionMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)
    m <- object@metrics
    if( object@algorithm == "glrm" ) {
      cat("Sum of Squared Error (Numeric): ", m$numerr)
      cat("\nMisclassification Error (Categorical): ", m$caterr)
      cat("\nNumber of Numeric Entries: ", m$numcnt)
      cat("\nNumber of Categorical Entries: ", m$catcnt)
    }else if(object@algorithm == "pca" ) {
        cat("No model metrics available for PCA")
    }
  } else print(NULL)
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OWordEmbeddingMetrics", contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OCoxPHMetrics", contains="H2OModelMetrics")

#' H2O Future Model
#'
#' A class to contain the information for background model jobs.
#' @slot job_key a character key representing the identification of the job process.
#' @slot model_id the final identifier for the model
#' @seealso \linkS4class{H2OModel} for the final model types.
#' @export
setClass("H2OModelFuture", representation(job_key="character", model_id="character"))

#' H2O Grid
#'
#' A class to contain the information about grid results
#' @slot grid_id the final identifier of grid
#' @slot model_ids  list of model IDs which are included in the grid object
#' @slot hyper_names  list of parameter names used for grid search
#' @slot failed_params  list of model parameters which caused a failure during model building,
#'                      it can contain a null value
#' @slot failure_details  list of detailed messages which correspond to failed parameters field
#' @slot failure_stack_traces  list of stack traces corresponding to model failures reported by
#'                             failed_params and failure_details fields
#' @slot failed_raw_params list of failed raw parameters
#' @slot summary_table table of models built with parameters and metric information.
#' @seealso \linkS4class{H2OModel} for the final model types.
#' @aliases H2OGrid
#' @export
setClass("H2OGrid", representation(grid_id = "character",
                                   model_ids = "list",
                                   hyper_names = "list",
                                   failed_params = "list",
                                   failure_details = "list",
                                   failure_stack_traces = "list",
                                   failed_raw_params = "matrix",
                                   summary_table = "ANY"))

#' Format grid object in user-friendly way
#'
#' @rdname H2OGrid-class
#' @param object an \code{H2OGrid} object.
#' @export
setMethod("show", "H2OGrid", function(object) {
  cat("H2O Grid Details\n")
  cat("================\n\n")
  cat("Grid ID:", object@grid_id, "\n")
  cat("Used hyper parameters: \n")
  lapply(object@hyper_names, function(name) { cat("  - ", name, "\n") })
  cat("Number of models:", length(object@model_ids), "\n")
  cat("Number of failed models:", length(object@failed_params), "\n\n")
  hyper_names <- object@hyper_names
  model_ids <- sapply(object@model_ids, function(model_id) { model_id })
  print(object@summary_table)
  if (length(object@failed_params) > 0) {
    # Extract failed parameters info
    params_failed <- lapply(1:length(hyper_names), function(idx) {
                      apply(object@failed_raw_params, 1, function(fp) { fp[idx] })
                    })
    names(params_failed) <- hyper_names
    status_failed <- rep("FAIL", length(object@failed_params))
    msgs_failed <- sapply(object@failure_details, function(msg) { paste0("\"", msg, "\"") })
    df_failed <- data.frame(params_failed, status_failed, msgs_failed)
    cat("Failed models\n")
    cat("-------------\n")
    print(df_failed, row.names = FALSE)
  }
})
#' Format grid object in user-friendly way
#'
#' @param object an \code{H2OGrid} object.
#' @param show_stack_traces  a flag to show stack traces for model failures
#' @export
setMethod("summary", "H2OGrid",
          function(object, show_stack_traces = FALSE) {
            show(object)
            cat("H2O Grid Summary\n")
            cat("================\n\n")
            cat("Grid ID:", object@grid_id, "\n")
            cat("Used hyper parameters: \n")
            lapply(object@hyper_names, function(name) { cat("  - ", name, "\n") })
            cat("Number of models:", length(object@model_ids), "\n")
            if (length(object@model_ids) > 0) {
              for (idx in 1:length(object@model_ids)) {
                cat("  - ", object@model_ids[[idx]], "\n")
              }
            }
            cat("\nNumber of failed models:", length(object@failed_params), "\n")
            if (length(object@failed_params) > 0) {
              for (idx in 1:length(object@failed_params)) {
                cat("  - ", object@failure_details[[idx]])
                if (show_stack_traces) {
                  cat(object@failure_stack_traces[[idx]], "\n")
                }
              }
            }

            if (!show_stack_traces && length(object@failed_params) > 0) {
              cat("\nNote: To see exception stack traces please pass parameter `show_stack_traces = T` to this function.\n")
            }
})

#'
#' The H2OFrame class
#'
#' This class represents an H2OFrame object
#'
#' @export
setClass("H2OFrame")

#'
#' The H2OAutoML class
#'
#' This class represents an H2OAutoML object
#'
#' @export
setClass("H2OAutoML", slots = c(project_name = "character",
                                leader = "H2OModel",
                                leaderboard = "H2OFrame"))
