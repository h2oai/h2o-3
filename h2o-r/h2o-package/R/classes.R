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
NULL 

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
#' This class represents the mutable aspects of a connection to an H2O cluster.
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
#' This class represents a connection to an H2O cluster.
#'
#' Because H2O is not a master-slave architecture, there is no restriction on which H2O node
#' is used to establish the connection between R (the client) and H2O (the server).
#'
#' A new H2O connection is established via the h2o.init() function, which takes as parameters
#' the `ip` and `port` of the machine running an instance to connect with. The default behavior
#' is to connect with a local instance of H2O at port 54321, or to boot a new local instance if one
#' is not found at port 54321.
#' @slot ip A \code{character} string specifying the IP address of the H2O cluster.
#' @slot port A \code{numeric} value specifying the port number of the H2O cluster.
#' @slot name A \code{character} value specifying the name of the H2O cluster.
#' @slot proxy A \code{character} specifying the proxy path of the H2O cluster.
#' @slot https Set this to TRUE to use https instead of http.
#' @slot cacert Path to a CA bundle file with root and intermediate certificates of trusted CAs.
#' @slot insecure Set this to TRUE to disable SSL certificate checking.
#' @slot username Username to login with.
#' @slot password Password to login with.
#' @slot use_spnego Set this to TRUE to use SPNEGO authentication.
#' @slot cookies Cookies to add to request
#' @slot context_path Context path which is appended to H2O server location.
#' @slot mutable An \code{H2OConnectionMutableState} object to hold the mutable state for the H2O connection.
#' @aliases H2OConnection
#' @export
setClass("H2OConnection",
         slots = c(
           ip = "character",
           port = "numeric",
           name = "character",
           proxy = "character",
           https = "logical",
           cacert = "character",
           insecure = "logical",
           username = "character",
           password = "character",
           use_spnego = "logical",
           cookies = "character",
           context_path = "character",
           mutable = "H2OConnectionMutableState"),
         prototype = prototype(
           ip = NA_character_,
           port = NA_integer_,
           name = NA_character_,
           proxy = NA_character_,
           https = FALSE,
           cacert = NA_character_,
           insecure = FALSE,
           username = NA_character_,
           password = NA_character_,
           use_spnego = FALSE,
           cookies = NA_character_,
           context_path = NA_character_,
           mutable = new("H2OConnectionMutableState")))

setClassUnion("H2OConnectionOrNULL", c("H2OConnection", "NULL"))

#' @rdname H2OConnection-class
#' @param object an \code{H2OConnection} object.
#' @export
setMethod("show", "H2OConnection", function(object) {
  cat("IP Address:", object@ip,                 "\n")
  cat("Port      :", object@port,               "\n")
  cat("Name      :", object@name,               "\n")
  cat("Session ID:", object@mutable$session_id, "\n")
  cat("Key Count :", object@mutable$key_count,  "\n")
})

#' Virtual Keyed class
#'
#' Base class for all objects having a persistent representation on backend.
#'
#' @export
setClass("Keyed", contains="VIRTUAL")
#' Method on \code{Keyed} objects allowing to obtain their key.
#'
#' @param object A \code{Keyed} object
#' @return the string key holding the persistent object.
#' @export
setGeneric("h2o.keyof", function(object) {
  standardGeneric("h2o.keyof")
})
#' @rdname h2o.keyof
setMethod("h2o.keyof", signature(object = "Keyed"), function(object) {
  stop("`keyof` not implemented for this object type.")
})

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cluster,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot model_id A \code{character} string specifying the key for the model fit in the H2O cluster's key-value store.
#' @slot algorithm A \code{character} string specifying the algorithm that were used to fit the model.
#' @slot parameters A \code{list} containing the parameter settings that were used to fit the model that differ from the defaults.
#' @slot allparameters A \code{list} containg all parameters used to fit the model.
#' @slot params A \code{list} containing default, set, and actual parameters.
#' @slot have_pojo A \code{logical} indicating whether export to POJO is supported
#' @slot have_mojo A \code{logical} indicating whether export to MOJO is supported
#' @slot model A \code{list} containing the characteristics of the model returned by the algorithm.
#' @aliases H2OModel
#' @export
setClass("H2OModel",
         slots =  c(
           model_id = "character",
           algorithm = "character",
           parameters = "list",
           allparameters = "list",
           params = "list",
           have_pojo = "logical",
           have_mojo = "logical",
           model = "list"),
         prototype = prototype(model_id = NA_character_),
         contains = c("Keyed", "VIRTUAL"))

# TODO: make a more model-specific constructor
.newH2OModel <- function(Class, model_id, ...) {
  new(Class, model_id=model_id, ...)
}

#' @rdname h2o.keyof
setMethod("h2o.keyof", signature("H2OModel"), function(object) object@model_id)

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
  if( !is.null(m$coefficients_table) ){
    print(m$coefficients_table)
  } else if(o@algorithm == 'generic' && !is.null(m$training_metrics@metrics$`coefficients_table`)){
    # In case of generic model, coefficient_table is part of the metrics object
    print(m$training_metrics@metrics$`coefficients_table`)
  }

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
    if( !is.null(tm$pr_auc)                                             )  cat("\nAUCPR: (Extract with `h2o.aucpr`)", tm$pr_auc)
  if( !is.null(tm$Gini)                                            )  cat("\nGini: (Extract with `h2o.gini`)", tm$Gini)
  if( !is.null(tm$null_deviance)                                   )  cat("\nNull Deviance: (Extract with `h2o.nulldeviance`)", tm$null_deviance)
  if( !is.null(tm$residual_deviance)                               )  cat("\nResidual Deviance: (Extract with `h2o.residual_deviance`)", tm$residual_deviance)
  if(!is.null(o@algorithm) && o@algorithm %in% c("gam","glm","gbm","drf","xgboost","infogram","generic")) {
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
  if (arg != "test") {
    if( !is.null(tm$multinomial_auc_table)                                 )  cat(paste0("\nAUC Table: Extract with `h2o.multinomial_auc_table(<model>,", arg, " = TRUE)`\n"))
  } else {
    if( !is.null(tm$multinomial_auc_table)                                 )  cat(paste0("\nAUC Table: Extract with `h2o.multinomial_auc_table(<model>, <data>)`\n"))
  }
  if( !is.null(tm$multinomial_auc_table)                                 )  { cat("=======================================================================\n"); print(tm$multinomial_auc_table); }
  cat("\n")
  if (arg != "test") {
    if( !is.null(tm$multinomial_aucpr_table)                                 )  cat(paste0("\nPR AUC Table: Extract with `h2o.multinomial_aucpr_table(<model>,", arg, " = TRUE)`\n"))
  } else {
    if( !is.null(tm$multinomial_aucpr_table)                                 )  cat(paste0("\nPR AUC Table: Extract with `h2o.multinomial_aucpr_table(<model>, <data>)`\n"))
  }
  if( !is.null(tm$multinomial_aucpr_table)                                 )  { cat("=======================================================================\n"); print(tm$multinomial_auc_table); }
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
setClass("H2OBinomialUpliftModel",    contains="H2OModel")
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
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cluster,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot model_id A \code{character} string specifying the key for the model fit in the H2O cluster's key-value store.
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
#' @rdname H2OModel-class
#' @export
setClass("H2OAnomalyDetectionModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OTargetEncoderModel", contains="H2OModel")

#'
#' The H2OCoxPHModel object.
#'
#' Virtual object representing H2O's CoxPH Model.
#'
#' @aliases H2OCoxPHModel
#' @export
setClass("H2OCoxPHModel", contains="H2OModel")

#' @rdname H2OCoxPHModel-class
#' @param object an \code{H2OCoxPHModel} object.
#' @export
setMethod("show", "H2OCoxPHModel", function(object) {
  requireNamespace("survival")
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
#' The H2OCoxPHModelSummary object.
#'
#' Wrapper object for summary information compatible with survival package.
#'
#' @slot summary A \code{list} containing the a summary compatible with CoxPH summary used in the survival package.
#' @aliases H2OCoxPHModelSummary
#' @export
setClass("H2OCoxPHModelSummary", slots = c(summary = "list"))

#' @rdname H2OCoxPHModelSummary-class
#' @param object An \code{H2OCoxPHModelSummary} object.
#' @export
setMethod("show", "H2OCoxPHModelSummary", function(object) {
  requireNamespace("survival")
  get("print.summary.coxph", getNamespace("survival"))(object@summary)
})

#'
#' Summary method for H2OCoxPHModel objects
#'
#' @param object an \code{H2OCoxPHModel} object.
#' @param conf.int a specification of the confidence interval.
#' @param scale a scale.
#' @importFrom stats qnorm
#' @export
setMethod("summary", "H2OCoxPHModel",
          function(object, conf.int = 0.95, scale = 1) {
            res <- .as.survival.coxph.summary(object@model)
            if (conf.int == 0)
              res@summary$conf.int <- NULL
            else {
              z <- qnorm((1 + conf.int)/2, 0, 1)
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

#' @rdname H2OCoxPHModel-class
#' @param ... additional arguments to pass on.
#' @export
coef.H2OCoxPHModel        <- function(object, ...) .as.survival.coxph.model(object@model)$coefficients

#' @rdname H2OCoxPHModelSummary-class
#' @param ... additional arguments to pass on.
#' @export
coef.H2OCoxPHModelSummary <- function(object, ...) object@summary$coefficients

#' @rdname H2OCoxPHModel-class
#' @param fit an \code{H2OCoxPHModel} object.
#' @param scale optional numeric specifying the scale parameter of the model.
#' @param k numeric specifying the weight of the equivalent degrees of freedom.
#' @export
extractAIC.H2OCoxPHModel <- function(fit, scale, k = 2, ...) {
  fun <- get("extractAIC.coxph", getNamespace("stats"))
  if (missing(scale))
    fun(.as.survival.coxph.model(fit@model), k = k)
  else
    fun(.as.survival.coxph.model(fit@model), scale = scale, k = k)
}

#' @rdname H2OCoxPHModel-class
#' @export
logLik.H2OCoxPHModel <- function(object, ...) {
  requireNamespace("survival")
  get("logLik.coxph", getNamespace("survival"))(.as.survival.coxph.model(object@model), ...)
}

#' @rdname H2OCoxPHModel-class
#' @param formula an \code{H2OCoxPHModel} object.
#' @param newdata an optional \code{H2OFrame} or \code{data.frame} with the same
#' variable names as those that appear in the \code{H2OCoxPHModel} object.
#' @importFrom stats as.formula
#' @export survfit.H2OCoxPHModel
survfit.H2OCoxPHModel <-
function(formula, newdata, ...)
{
  requireNamespace("survival")
  if (missing(newdata)) {
    if (!is.null(formula@allparameters$stratify_by) ||
        !is.null(formula@allparameters$interactions) ||
        !is.null(formula@allparameters$interaction_pairs)) {
      stop("Models with strata or interaction terms require newdata argument")
    }
    newdata <- as.data.frame(c(as.list(as.data.frame(formula@model$x_mean_cat)),
                               as.list(as.data.frame(formula@model$x_mean_num)),
                               as.list(as.data.frame(formula@model$mean_offset))),
                             col.names = c(formula@model$coefficients_table$names,
                                           formula@model$offset_names))
  }
  if (is.data.frame(newdata))
    capture.output(newdata <- as.h2o(newdata))

  # Code below has calculation performed in R
  capture.output(suppressWarnings(pred <- as.data.frame(h2o.predict(formula, newdata))[[1L]]))
  res <- list(n         = formula@model$n,
              time      = formula@model$time,
              n.risk    = formula@model$n_risk,
              n.event   = formula@model$n_event,
              n.censor  = formula@model$n_censor,
              surv      = NULL,
              type      = ifelse(length(as.formula(formula@model$formula)[[2L]]) == 3L, "right", "counting"),
              cumhaz    = formula@model$cumhaz_0,
              baseline_hazard    = formula@model$bazeline_hazard,
              std.err   = NULL,
              upper     = NULL,
              lower     = NULL,
              conf.type = NULL,
              conf.int  = NULL,
              call      = match.call())
  if (length(pred) == 1L)
    res$cumhaz <- res$cumhaz * exp(pred)
  else
    res$cumhaz <- outer(res$cumhaz, exp(pred), FUN = "*")
  res$surv <- exp(- res$cumhaz)
  oldClass(res) <- c("survfit.H2OCoxPHModel", "survfit.cox", "survfit")
  res
}

#' @rdname H2OCoxPHModel-class
#' @export
vcov.H2OCoxPHModel <- function(object, ...) {
  requireNamespace("survival")
  get("vcov.coxph", getNamespace("survival"))(.as.survival.coxph.model(object@model), ...)
}

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
          slots = c(
            algorithm = "character",
            on_train = "logical",
            on_valid = "logical",
            on_xval = "logical",
            metrics = "listOrNull"),
         prototype = prototype(algorithm = NA_character_, on_train = FALSE, on_valid = FALSE, on_xval = FALSE, metrics = NULL),
         contains = "VIRTUAL")

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
    cat("AUCPR:  ", object@metrics$pr_auc, "\n", sep="")
    cat("Gini:  ", object@metrics$Gini, "\n", sep="")
    if(!is.null(object@algorithm) && object@algorithm %in% c("gam","glm","gbm","drf","xgboost","infogram","generic")) {

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
setClass("H2OBinomialUpliftMetrics",    contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OBinomialUpliftMetrics", function(object) {
    callNextMethod(object)  # call to the super
    cat("ATE: ", object@metrics$ate, "\n", sep="" )
    cat("ATT: ", object@metrics$atc, "\n", sep="" )
    cat("ATC: ", object@metrics$att, "\n", sep="" )
    cat("Default AUUC:  ", object@metrics$AUUC, "\n", sep="")
    cat("All types of AUUC:  ", "\n", sep="")
    print(object@metrics$auuc_table)
    cat("Default Qini value: ", object@metrics$qini, "\n", sep="")
    cat("All types of AECU values:  ", "\n", sep="")
    print(object@metrics$aecu_table)
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
  if(!is.null(object@algorithm) && object@algorithm %in% c("glm") && exists("sefe", where=object@metrics)) {
      cat("sefe:  ", object@metrics$sefe, "\n", sep="")
      cat("sere:  ", object@metrics$sere, "\n", sep="")
      cat("fixedf:  ", object@metrics$fixedf, "\n", sep="")
      cat("ranef:  ", object@metrics$ranef, "\n", sep="")
      cat("randc:  ", object@metrics$randc, "\n", sep="")
      cat("varfix:  ", object@metrics$varfix, "\n", sep="")
      cat("varranef:  ", object@metrics$varranef, "\n", sep="")
      cat("converge:  ", object@metrics$converge, "\n", sep="")
      cat("dfrefe:  ", object@metrics$dfrefe, "\n", sep="")
      cat("summvc1:  ", object@metrics$summvc1, "\n", sep="")
      cat("summvc2:  ", object@metrics$summvc2, "\n", sep="")
      cat("bad:  ", object@metrics$bad, "\n", sep="")
      if (exists("hlik", where=object@metrics) && !is.null(object@metrics$hlik)) {
      cat("hlik:  ", object@metrics$hlik, "\n", sep="")
      cat("pvh:  ", object@metrics$pvh, "\n", sep="")
      cat("pbvh:  ", object@metrics$pbvh, "\n", sep="")
      cat("caic:  ", object@metrics$caic, "\n", sep="")
      }
  } else {
      cat("Mean Residual Deviance :  ", h2o.mean_residual_deviance(object), "\n", sep="")
  }
  if(!is.null(object@algorithm) && object@algorithm %in% c("gam","glm","generic") && exists("r2", where=object@metrics)) {
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
    if( !is.null(m$centroid_stats) ){
      print(m$centroid_stats)
    } else {
      cat("Centroid statistics are not available.")
    }
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

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OAnomalyDetectionMetrics", contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OAnomalyDetectionMetrics", function(object) {
  callNextMethod(object)  # call to the super
  cat("Anomaly Score:", object@metrics$mean_score, "\n")
  cat("Normalized Anomaly Score:", object@metrics$mean_normalized_score, "\n")
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OTargetEncoderMetrics", contains="H2OModelMetrics")

#' H2O Future Model
#'
#' A class to contain the information for background model jobs.
#' @slot job_key a character key representing the identification of the job process.
#' @slot model_id the final identifier for the model
#' @seealso \linkS4class{H2OModel} for the final model types.
#' @export
setClass("H2OModelFuture", slots = c(job_key = "character", model_id = "character"))

#' H2O Future Segment Models
#'
#' A class to contain the information for background segment models jobs.
#' @slot job_key a character key representing the identification of the job process.
#' @slot segment_models_id the final identifier for the segment models collections
#' @seealso \linkS4class{H2OSegmentModels} for the final segment models types.
#' @export
setClass("H2OSegmentModelsFuture", slots = c(job_key = "character", segment_models_id = "character"))

#' H2O Segment Models
#'
#' A class to contain the information for segment models.
#' @slot segment_models_id the  identifier for the segment models collections
#' @export
setClass("H2OSegmentModels", slots = c(segment_models_id = "character"))

#' H2O Data Transformer
#'
#' A representation of a transformer used in an H2O Pipeline
#' @slot id the unique identifier for the transformer.
#' @slot name the readable name for the transformer and its variants.
#' @slot description a description of what the transformer does on data.
#' @export
setClass("H2ODataTransformer", slots = c(id = "character", name = "character", description = "character"))

#' @rdname h2o.keyof
setMethod("h2o.keyof", signature("H2ODataTransformer"), function(object) object@id)

#' H2O Pipeline
#'
#' A representation of a pipeline model consisting in a sequence of transformers applied to data
#'   and usually followed by a final estimator model.
#' @slot transformers the list of H2O Data Transformers in the pipeline.
#' @slot estimator_model the final estimator model.
setClass("H2OPipeline", contains="H2OModel",
         slots = c(
             transformers = "list",
             estimator_model = "H2OModel"
         ))

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
setClass("H2OGrid",
         slots = c(
           grid_id = "character",
           model_ids = "list",
           hyper_names = "list",
           failed_params = "list",
           failure_details = "list",
           failure_stack_traces = "list",
           failed_raw_params = "matrix",
           summary_table = "ANY"))

#' @rdname h2o.keyof
setMethod("h2o.keyof", signature("H2OGrid"), function(object) object@grid_id)

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
setClass("H2OFrame", contains = c("Keyed", "environment"))
#' @rdname h2o.keyof
setMethod("h2o.keyof", signature("H2OFrame"), function(object) attr(object, "id"))

setClassUnion("CharacterOrNULL", c("character", "NULL"))
setClassUnion("numericOrNULL", c("numeric", "NULL"))
setClassUnion("H2OFrameOrNULL", c("H2OFrame", "NULL"))

#' H2OInfogram class
#'
#' H2OInfogram class contains a subset of what a normal H2OModel will return
#' @slot model_id string returned as part of every \code{H2OModel}
#' @slot algorithm string denoting the algorithm used to build infogram
#' @slot admissible_features string array denoting all predictor names which pass the cmi and relelvance threshold
#' @slot admissible_features_valid string array denoting all predictor names which pass the cmi and relelvance threshold from validation frame
#' @slot admissible_features_xval string array denoting all predictor names which pass the cmi and relelvance threshold from cv holdout set
#' @slot net_information_threshold numeric value denoting threshold used for predictor selection
#' @slot total_information_threshold numeric value denoting threshold used for predictor selection
#' @slot safety_index_threshold numeric value denoting threshold used for predictor selection
#' @slot relevance_index_threshold numeric value denoting threshold used for predictor selection
#' @slot admissible_score \code{H2OFrame} that contains columns, admissible, admissible_index, relevance, cmi, cmi_raw
#' @slot admissible_score_valid \code{H2OFrame} that contains columns, admissible, admissible_index, relevance, cmi, cmi_raw from validation frame
#' @slot admissible_score_xval \code{H2OFrame} that contains averages of columns, admissible, admissible_index, relevance, cmi, cmi_raw from cv hold-out
#' @export
setClass("H2OInfogram", slots = c(model_id = "character", 
                                  algorithm = "character", 
                                  admissible_features = "CharacterOrNULL",  
                                  admissible_features_valid = "CharacterOrNULL", 
                                  admissible_features_xval = "CharacterOrNULL",
                                  net_information_threshold = "numericOrNULL", 
                                  total_information_threshold = "numericOrNULL", 
                                  safety_index_threshold = "numericOrNULL", 
                                  relevance_index_threshold = "numericOrNULL", 
                                  admissible_score = "H2OFrame", 
                                  admissible_score_valid = "H2OFrameOrNULL", 
                                  admissible_score_xval = "H2OFrameOrNULL"))

#' Method on \code{H2OInfogram} object which in this case is to instantiate and initialize it
#'
#' @param .Object An \code{H2OInfogram} object
#' @param model_id string returned as part of every H2OModel
#' @param ... additional arguments to pass on
#' @return A \code{H2OInfogram} object
#' @export
setMethod("initialize", "H2OInfogram", function(.Object, model_id, ...) {
  if (!missing(model_id)) {
    infogram_model <- h2o.getModel(model_id)
    if (is(infogram_model, "H2OModel") &&
        (infogram_model@algorithm == "infogram")) {
      .Object@model_id <- infogram_model@model_id
      .Object@algorithm <- infogram_model@algorithm
      if (!is.null(infogram_model@model$admissible_features) && !is.list(infogram_model@model$admissible_features)) {
        .Object@admissible_features <-
        infogram_model@model$admissible_features
      }
      .Object@net_information_threshold <- infogram_model@parameters$net_information_threshold
      .Object@total_information_threshold <- infogram_model@parameters$total_information_threshold 
      .Object@safety_index_threshold <- infogram_model@parameters$safety_index_threshold
      .Object@relevance_index_threshold <- infogram_model@parameters$relevance_index_threshold
      .Object@admissible_score <- h2o.getFrame(infogram_model@model$admissible_score_key$name)
      .Object@net_information_threshold <-
        infogram_model@parameters$net_information_threshold
      .Object@total_information_threshold <-
        infogram_model@parameters$total_information_threshold
      .Object@safety_index_threshold <-
        infogram_model@parameters$safety_index_threshold
      .Object@relevance_index_threshold <-
        infogram_model@parameters$relevance_index_threshold
      .Object@admissible_score <-
        h2o.getFrame(infogram_model@model$admissible_score_key$name)
      if (!is.null(infogram_model@model$admissible_features_valid) &&
          !is.list(infogram_model@model$admissible_features_valid)) {
        .Object@admissible_features_valid <-
          infogram_model@model$admissible_features_valid
      } else {
        .Object@admissible_features_valid <- NULL
      }
      if (!is.null(infogram_model@model$admissible_features_xval) &&
          !is.list(infogram_model@model$admissible_features_xval)) {
        .Object@admissible_features_xval <-
          infogram_model@model$admissible_features_xval
      } else {
        .Object@admissible_features_xval <- NULL
      }
      if (!is.null(infogram_model@model$admissible_score_key_valid)) {
        .Object@admissible_score_valid <-
          h2o.getFrame(infogram_model@model$admissible_score_key_valid$name)
      } else {
        .Object@admissible_score_valid <- NULL
      }
      if (!is.null(infogram_model@model$admissible_score_key_xval)) {
        .Object@admissible_score_xval <-
          h2o.getFrame(infogram_model@model$admissible_score_key_xval$name)
      } else {
        .Object@admissible_score_xval <- NULL
      }
      return(.Object)
    } else {
      stop('Input must be H2OModel with algorithm == "infogram".')
    }
  } else {
    stop("A model Id must be used to instantiate a H2OInfogram.")
  }
})

#' wrapper function for instantiating H2OInfogram
#' @param model_id is string of H2OModel object
#' @param ... parameters to algorithm, admissible_features, ...
#' @return A \code{H2OInfogram} object
#' @export
H2OInfogram <- function(model_id, ...) {
  initialize(new("H2OInfogram"), model_id = model_id, ...)
}

#'
#' The H2OAutoML class
#'
#' This class represents an H2OAutoML object
#'
#' @export
setClass("H2OAutoML", slots = c(project_name = "character",
                                leader = "H2OModel",
                                leaderboard = "H2OFrame",
                                event_log = "H2OFrame",
                                modeling_steps = "list",
                                training_info = "list"),
                      contains = "Keyed")
#' @rdname h2o.keyof
setMethod("h2o.keyof", signature("H2OAutoML"), function(object) attr(object, "id"))

#'
#' Format AutoML object in user-friendly way
#'
#' @param object an \code{H2OAutoML} object.
#' @export
setMethod("show", signature("H2OAutoML"), function(object) {
  cat("AutoML Details\n")
  cat("==============\n")
  cat("Project Name:", object@project_name, "\n")
  cat("Leader Model ID:", object@leader@model_id, "\n")
  cat("Algorithm:", object@leader@algorithm, "\n\n")

  cat("Total Number of Models Trained:", nrow(object@leaderboard), "\n")
  cat("Start Time:",
      as.character(as.POSIXct(as.numeric(object@training_info$start_epoch), origin="1970-01-01")), h2o.getTimezone(), "\n")
  cat("End Time:",
      as.character(as.POSIXct(as.numeric(object@training_info$stop_epoch), origin="1970-01-01")), h2o.getTimezone(), "\n")
  cat("Duration:", object@training_info$duration_secs, "s\n\n")

  cat("Leaderboard\n")
  cat("===========\n")
  print(object@leaderboard, n = 10)

  invisible(NULL)
})

#' Format AutoML object in user-friendly way
#'
#' @param object an \code{H2OAutoML} object.
#' @export
setMethod("summary", signature("H2OAutoML"), function(object) {
  cat("AutoML Summary\n")
  cat("==============\n")
  cat("Project Name:", object@project_name, "\n")
  cat("Leader Model ID:", object@leader@model_id, "\n")
  cat("Algorithm:", object@leader@algorithm, "\n\n")

  cat("Total Number of Models Trained:", nrow(object@leaderboard), "\n")
  cat("Start Time:",
      as.character(as.POSIXct(as.numeric(object@training_info$start_epoch), origin="1970-01-01")), h2o.getTimezone(), "\n")
  cat("End Time:",
      as.character(as.POSIXct(as.numeric(object@training_info$stop_epoch), origin="1970-01-01")), h2o.getTimezone(), "\n")
  cat("Duration:", object@training_info$duration_secs, "s\n\n")

  cat("Leaderboard\n")
  cat("===========\n")
  print(h2o.get_leaderboard(object, "ALL"), n = Inf)

  invisible(NULL)
})

#'
#' Retrieve the variable importance.
#'
#' @param object An H2O object.
#' @param ... Additional arguments for specific use-cases.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip"
#' pros <- h2o.importFile(f)
#' response <- "GLEASON"
#' predictors <- c("ID", "AGE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
#' aml <- h2o.automl(x = predictors, y = response, training_frame = pros, max_runtime_secs = 60)
#'
#' h2o.varimp(aml, top_n = 20)  # get variable importance matrix for the top 20 models
#'
#' h2o.varimp(aml@leader)  # get variable importance for the leader model
#' }
#' @export
setGeneric("h2o.varimp", function(object, ...)
  warning(paste0("No variable importances for ", class(object)), call. = FALSE))

#'
#' Retrieve the variable importance.
#'
#' @param object An \linkS4class{H2OModel} object.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip"
#' pros <- h2o.importFile(f)
#' response <- "GLEASON"
#' predictors <- c("ID", "AGE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
#' model <- h2o.glm(x = predictors, y = response, training_frame = pros)
#' h2o.varimp(model)
#' }
#' @export
setMethod("h2o.varimp", signature("H2OModel"), function(object) {
  vi <- object@model$variable_importances
  if( is.null(vi) ) {
    warning("This model doesn't have variable importances", call. = FALSE)
    return(invisible(NULL))
  }
  return(vi)
})

#'
#' Retrieve the variable importance.
#'
#' @param object An \linkS4class{H2OAutoML} object.
#' @param top_n Show at most top_n models
#' @param num_of_features Integer specifying the number of features returned based on the maximum
#'                        importance across the models. Use NULL for unlimited. Defaults to NULL.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip"
#' pros <- h2o.importFile(f)
#' response <- "GLEASON"
#' predictors <- c("ID", "AGE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
#' aml <- h2o.automl(x = predictors, y = response, training_frame = pros, max_runtime_secs = 60)
#' h2o.varimp(aml)
#' }
#' @export
setMethod("h2o.varimp", signature("H2OAutoML"), function(object, top_n = 20, num_of_features = NULL) {
  .varimp_matrix(object, top_n = top_n, num_of_features = num_of_features)
})

#'
#' Retrieve the variable importance.
#'
#' @param object A leaderboard frame.
#' @param num_of_features Integer specifying the number of features returned based on the maximum
#'                        importance across the models. Use NULL for unlimited. Defaults to NULL.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip"
#' pros <- h2o.importFile(f)
#' response <- "GLEASON"
#' predictors <- c("ID", "AGE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
#' aml <- h2o.automl(x = predictors, y = response, training_frame = pros, max_runtime_secs = 60)
#' h2o.varimp(aml@leaderboard[1:5,])
#' }
#' @export
setMethod("h2o.varimp", signature("H2OFrame"), function(object, num_of_features = NULL) {
  if (! "model_id" %in% names(object)){
    stop("This is not a leaderboard frame. Only frames containing `model_id` column are supported.")
  }
  .varimp_matrix(object, num_of_features = num_of_features)
})
