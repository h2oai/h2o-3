def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('custom')
        return param
    return None  # param untouched


extensions = dict(
    validate_params="""
# if (!is.null(beta_constraints)) {
#     if (!inherits(beta_constraints, 'data.frame') && !is.H2OFrame(beta_constraints))
#       stop(paste('`beta_constraints` must be an H2OH2OFrame or R data.frame. Got: ', class(beta_constraints)))
#     if (inherits(beta_constraints, 'data.frame')) {
#       beta_constraints <- as.h2o(beta_constraints)
#     }
# }
if (inherits(beta_constraints, 'data.frame')) {
  beta_constraints <- as.h2o(beta_constraints)
}
""",
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 "interactions", "nfolds", "beta_constraints", "missing_values_handling"],
    set_params="""
if( !missing(interactions) ) {
  # interactions are column names => as-is
  if( is.character(interactions) )       parms$interactions <- interactions
  else if( is.numeric(interactions) )    parms$interactions <- names(training_frame)[interactions]
  else stop(\"Don't know what to do with interactions. Supply vector of indices or names\")
}
# For now, accept nfolds in the R interface if it is 0 or 1, since those values really mean do nothing.
# For any other value, error out.
# Expunge nfolds from the message sent to H2O, since H2O doesn't understand it.
if (!missing(nfolds) && nfolds > 1)
  parms$nfolds <- nfolds
if(!missing(beta_constraints))
  parms$beta_constraints <- beta_constraints
  if(!missing(missing_values_handling))
    parms$missing_values_handling <- missing_values_handling
""",
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
if (HGLM && is.null(random_columns)) stop("HGLM: must specify random effect column!")
if (HGLM && (!is.null(random_columns))) {
  temp <- .verify_dataxy(training_frame, random_columns, y)
  random_columns <- temp$x_i-1  # change column index to numeric column indices starting from 0
}
if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y    
    """,
    with_model="""
model@model$coefficients <- model@model$coefficients_table[,2]
names(model@model$coefficients) <- model@model$coefficients_table[,1]
if (!(is.null(model@model$random_coefficients_table))) {
    model@model$random_coefficients <- model@model$random_coefficients_table[,2]
    names(model@model$random_coefficients) <- model@model$random_coefficients_table[,1]
}
""",
    module="""
#' Set betas of an existing H2O GLM Model
#'
#' This function allows setting betas of an existing glm model.
#' @param model an \linkS4class{H2OModel} corresponding from a \code{h2o.glm} call.
#' @param beta a new set of betas (a named vector)
#' @export
h2o.makeGLMModel <- function(model,beta) {
  res = .h2o.__remoteSend(method="POST", .h2o.__GLMMakeModel, model=model@model_id, names = paste("[",paste(paste("\\\"",names(beta),"\\\"",sep=""), collapse=","),"]",sep=""), beta = paste("[",paste(as.vector(beta),collapse=","),"]",sep=""))
  m <- h2o.getModel(model_id=res$model_id$name)
  m@model$coefficients <- m@model$coefficients_table[,2]
  names(m@model$coefficients) <- m@model$coefficients_table[,1]
  m
}

#' Extract full regularization path from a GLM model
#'
#' Extract the full regularization path from a GLM model (assuming it was run with the lambda search option).
#'
#' @param model an \linkS4class{H2OModel} corresponding from a \code{h2o.glm} call.
#' @export
h2o.getGLMFullRegularizationPath <- function(model) {
  res = .h2o.__remoteSend(method="GET", .h2o.__GLMRegPath, model=model@model_id)
  colnames(res$coefficients) <- res$coefficient_names
  if(!is.null(res$coefficients_std) && length(res$coefficients_std) > 0L) {
    colnames(res$coefficients_std) <- res$coefficient_names
  }
  res
}

#' Compute weighted gram matrix.
#'
#' @param X an \linkS4class{H2OModel} corresponding to H2O framel.
#' @param weights character corresponding to name of weight vector in frame.
#' @param use_all_factor_levels logical flag telling h2o whether or not to skip first level of categorical variables during one-hot encoding.
#' @param standardize logical flag telling h2o whether or not to standardize data
#' @param skip_missing logical flag telling h2o whether skip rows with missing data or impute them with mean
#' @export
h2o.computeGram <- function(X,weights="", use_all_factor_levels=FALSE,standardize=TRUE,skip_missing=FALSE) {
  res = .h2o.__remoteSend(method="GET", .h2o.__ComputeGram, X=h2o.getId(X),W=weights,use_all_factor_levels=use_all_factor_levels,standardize=standardize,skip_missing=skip_missing)
  h2o.getFrame(res$destination_frame$name)
}

##' Start an H2O Generalized Linear Model Job
##'
##' Creates a background H2O GLM job.
##' @inheritParams h2o.glm
##' @return Returns a \linkS4class{H2OModelFuture} class object.
##' @export
#h2o.startGLMJob <- function(x, y, training_frame, model_id, validation_frame,
#                    #AUTOGENERATED Params
#                    max_iterations = 50,
#                    beta_epsilon = 0,
#                    solver = c("IRLSM", "L_BFGS"),
#                    standardize = TRUE,
#                    family = c("gaussian", "binomial", "poisson", "gamma", "tweedie"),
#                    link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"),
#                    tweedie_variance_power = NaN,
#                    tweedie_link_power = NaN,
#                    alpha = 0.5,
#                    prior = 0.0,
#                    lambda = 1e-05,
#                    lambda_search = FALSE,
#                    nlambdas = -1,
#                    lambda_min_ratio = 1.0,
#                    nfolds = 0,
#                    beta_constraints = NULL,
#                    ...
#                    )
#{
#  # if (!is.null(beta_constraints)) {
#  #     if (!inherits(beta_constraints, "data.frame") && !is.H2OFrame("H2OFrame"))
#  #       stop(paste("`beta_constraints` must be an H2OH2OFrame or R data.frame. Got: ", class(beta_constraints)))
#  #     if (inherits(beta_constraints, "data.frame")) {
#  #       beta_constraints <- as.h2o(beta_constraints)
#  #     }
#  # }
#
#  if (!is.H2OFrame(training_frame))
#      tryCatch(training_frame <- h2o.getFrame(training_frame),
#               error = function(err) {
#                 stop("argument \"training_frame\" must be a valid H2OFrame or model ID")
#              })
#
#    parms <- list()
#    args <- .verify_dataxy(training_frame, x, y)
#    parms$ignored_columns <- args$x_ignore
#    parms$response_column <- args$y
#    parms$training_frame  <- training_frame
#    parms$beta_constraints <- beta_constraints
#    if(!missing(model_id))
#      parms$model_id <- model_id
#    if(!missing(validation_frame))
#      parms$validation_frame <- validation_frame
#    if(!missing(max_iterations))
#      parms$max_iterations <- max_iterations
#    if(!missing(beta_epsilon))
#      parms$beta_epsilon <- beta_epsilon
#    if(!missing(solver))
#      parms$solver <- solver
#    if(!missing(standardize))
#      parms$standardize <- standardize
#    if(!missing(family))
#      parms$family <- family
#    if(!missing(link))
#      parms$link <- link
#    if(!missing(tweedie_variance_power))
#      parms$tweedie_variance_power <- tweedie_variance_power
#    if(!missing(tweedie_link_power))
#      parms$tweedie_link_power <- tweedie_link_power
#    if(!missing(alpha))
#      parms$alpha <- alpha
#    if(!missing(prior))
#      parms$prior <- prior
#    if(!missing(lambda))
#      parms$lambda <- lambda
#    if(!missing(lambda_search))
#      parms$lambda_search <- lambda_search
#    if(!missing(nlambdas))
#      parms$nlambdas <- nlambdas
#    if(!missing(lambda_min_ratio))
#      parms$lambda_min_ratio <- lambda_min_ratio
#    if(!missing(nfolds))
#      parms$nfolds <- nfolds
#
#    .h2o.startModelJob('glm', parms, h2oRestApiVersion=.h2o.__REST_API_VERSION)
#}
""",
)


doc = dict(
    preamble="""
Fit a generalized linear model

Fits a generalized linear model, specified by a response variable, a set of predictors, and a
description of the error distribution.
""",
    returns="""
A subclass of \code{\linkS4class{H2OModel}} is returned. The specific subclass depends on the machine
learning task at hand (if it's binomial classification, then an \code{\linkS4class{H2OBinomialModel}} is
returned, if it's regression then a \code{\linkS4class{H2ORegressionModel}} is returned). The default print-
out of the models is shown, but further GLM-specifc information can be queried out of the object. To access
these various items, please refer to the seealso section below. Upon completion of the GLM, the resulting
object has coefficients, normalized coefficients, residual/null deviance, aic, and a host of model metrics
including MSE, AUC (for logistic regression), degrees of freedom, and confusion matrices. Please refer to the
more in-depth GLM documentation available here:
\\url{https://h2o-release.s3.amazonaws.com/h2o-dev/rel-shannon/2/docs-website/h2o-docs/index.html#Data+Science+Algorithms-GLM}
""",
    seealso="""
\code{\link{predict.H2OModel}} for prediction, \code{\link{h2o.mse}}, \code{\link{h2o.auc}},
\code{\link{h2o.confusionMatrix}}, \code{\link{h2o.performance}}, \code{\link{h2o.giniCoef}},
\code{\link{h2o.logloss}}, \code{\link{h2o.varimp}}, \code{\link{h2o.scoreHistory}}
""",
    examples="""
h2o.init()

# Run GLM of CAPSULE ~ AGE + RACE + PSA + DCAPS
prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
prostate = h2o.importFile(path = prostate_path)
h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), training_frame = prostate,
        family = "binomial", nfolds = 0, alpha = 0.5, lambda_search = FALSE)

# Run GLM of VOL ~ CAPSULE + AGE + RACE + PSA + GLEASON
predictors = setdiff(colnames(prostate), c("ID", "DPROS", "DCAPS", "VOL"))
h2o.glm(y = "VOL", x = predictors, training_frame = prostate, family = "gaussian",
        nfolds = 0, alpha = 0.1, lambda_search = FALSE)


# GLM variable importance
# Also see:
#   https://github.com/h2oai/h2o/blob/master/R/tests/testdir_demos/runit_demo_VI_all_algos.R
bank = h2o.importFile(
  path="https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv"
)
predictors = 1:20
target = "y"
glm = h2o.glm(x = predictors, 
              y = target, 
              training_frame = bank, 
              family = "binomial", 
              standardize = TRUE,
              lambda_search = TRUE)
h2o.std_coef_plot(glm, num_of_features = 20)
"""
)
