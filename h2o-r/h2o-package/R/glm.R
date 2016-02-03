#' H2O Generalized Linear Models
#'
#' Fit a generalized linear model, specified by a response variable, a set of predictors, and a description of the error distribution.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the GLM model.
#' @param y A character string or index that represent the response variable in the model.
#' @param training_frame An H2OFrame object containing the variables in the model.
#' @param model_id (Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
#' @param validation_frame An H2OFrame object containing the variables in the model.  Defaults to NULL.
#' @param max_iterations A non-negative integer specifying the maximum number of iterations.
#' @param ignore_const_cols A logical value indicating whether or not to ignore all the constant columns in the training frame.
#' @param beta_epsilon A non-negative number specifying the magnitude of the maximum difference between the coefficient estimates from successive iterations.
#'        Defines the convergence criterion for \code{h2o.glm}.
#' @param solver A character string specifying the solver used: IRLSM (supports more features), L_BFGS (scales better for datasets with many columns)
#' @param standardize A logical value indicating whether the numeric predictors should be standardized to have a mean of 0 and a variance of 1 prior to
#'        training the models.
#' @param family A character string specifying the distribution of the model:  gaussian, binomial, poisson, gamma, tweedie.
#' @param link A character string specifying the link function. The default is the canonical link for the \code{family}. The supported links for each of
#'        the \code{family} specifications are:\cr
#'        \code{"gaussian"}: \code{"identity"}, \code{"log"}, \code{"inverse"}\cr
#'        \code{"binomial"}: \code{"logit"}, \code{"log"}\cr
#'        \code{"poisson"}: \code{"log"}, \code{"identity"}\cr
#'        \code{"gamma"}: \code{"inverse"}, \code{"log"}, \code{"identity"}\cr
#'        \code{"tweedie"}: \code{"tweedie"}\cr
#' @param tweedie_variance_power A numeric specifying the power for the variance function when \code{family = "tweedie"}.
#' @param tweedie_link_power A numeric specifying the power for the link function when \code{family = "tweedie"}.
#' @param alpha A numeric in [0, 1] specifying the elastic-net mixing parameter.
#'                The elastic-net penalty is defined to be:
#'                \deqn{P(\alpha,\beta) = (1-\alpha)/2||\beta||_2^2 + \alpha||\beta||_1 = \sum_j [(1-\alpha)/2 \beta_j^2 + \alpha|\beta_j|]}
#'                making \code{alpha = 1} the lasso penalty and \code{alpha = 0} the ridge penalty.
#' @param lambda A non-negative shrinkage parameter for the elastic-net, which multiplies \eqn{P(\alpha,\beta)} in the objective function.
#'               When \code{lambda = 0}, no elastic-net penalty is applied and ordinary generalized linear models are fit.
#' @param prior (Optional) A numeric specifying the prior probability of class 1 in the response when \code{family = "binomial"}.
#'               The default prior is the observational frequency of class 1.
#' @param lambda_search A logical value indicating whether to conduct a search over the space of lambda values starting from the lambda max, given
#'                      \code{lambda} is interpreted as lambda min.
#' @param nlambdas The number of lambda values to use when \code{lambda_search = TRUE}.
#' @param lambda_min_ratio Smallest value for lambda as a fraction of lambda.max. By default if the number of observations is greater than the
#'                         the number of variables then \code{lambda_min_ratio} = 0.0001; if the number of observations is less than the number
#'                         of variables then \code{lambda_min_ratio} = 0.01.
#' @param beta_constraints A data.frame or H2OParsedData object with the columns ["names",
#'        "lower_bounds", "upper_bounds", "beta_given", "rho"], where each row corresponds to a predictor
#'        in the GLM. "names" contains the predictor names, "lower_bounds" and "upper_bounds" are the lower
#'        and upper bounds of beta, "beta_given" is some supplied starting values for beta, and "rho" is the
#'        proximal penalty constant that is used with "beta_given". If "rho" is not specified when "beta_given"
#'        is then we will take the default rho value of zero.
#' @param offset_column Specify the offset column.
#' @param weights_column Specify the weights column.
#' @param nfolds (Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty.
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation.
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not specified
#'        Must be "AUTO", "Random" or "Modulo".
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models.
#' @param intercept Logical, include constant term (intercept) in the model.
#' @param max_active_predictors (Optional) Convergence criteria for number of predictors when using L1 penalty.
#' @param objective_epsilon Convergence criteria. Converge if relative change in objective function is below this threshold.
#' @param gradient_epsilon Convergence criteria. Converge if gradient l-infinity norm is below this threshold.
#' @param non_negative Logical, allow only positive coefficients.
#' @param compute_p_values (Optional)  Logical, compute p-values, only allowed with IRLSM solver and no regularization. May fail if there are collinear predictors.
#' @param remove_collinear_columns (Optional)  Logical, valid only with no regularization. If set, co-linear columns will be automatically ignored (coefficient will be 0).
#' @param missing_values_handling (Optional) Controls handling of missing values. Can be either "MeanImputation" or "Skip". MeanImputation replaces missing values with mean for numeric and most frequent level for categorical,  Skip ignores observations with any missing value. Applied both during model training *AND* scoring.
#' @param max_runtime_secs Maximum allowed runtime in seconds for model training. Use 0 to disable.
#' @param ... (Currently Unimplemented)
#'        coefficients.
#'
#' @return A subclass of \code{\linkS4class{H2OModel}} is returned. The specific subclass depends on the machine learning task at hand
#'         (if it's binomial classification, then an \code{\linkS4class{H2OBinomialModel}} is returned, if it's regression then a
#'          \code{\linkS4class{H2ORegressionModel}} is returned). The default print-out of the models is shown, but further GLM-specifc
#'          information can be queried out of the object. To access these various items, please refer to the seealso section below.
#'
#'          Upon completion of the GLM, the resulting object has coefficients, normalized coefficients, residual/null deviance, aic,
#'          and a host of model metrics including MSE, AUC (for logistic regression), degrees of freedom, and confusion matrices. Please
#'          refer to the more in-depth GLM documentation available here: \url{http://h2o-release.s3.amazonaws.com/h2o-dev/rel-shannon/2/docs-website/h2o-docs/index.html#Data+Science+Algorithms-GLM},
#'
#' @seealso \code{\link{predict.H2OModel}} for prediction, \code{\link{h2o.mse}}, \code{\link{h2o.auc}},
#'          \code{\link{h2o.confusionMatrix}}, \code{\link{h2o.performance}}, \code{\link{h2o.giniCoef}}, \code{\link{h2o.logloss}},
#'          \code{\link{h2o.varimp}}, \code{\link{h2o.scoreHistory}}
#' @examples
#' \donttest{
#' h2o.init()
#'
#' # Run GLM of CAPSULE ~ AGE + RACE + PSA + DCAPS
#' prostatePath = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex = h2o.importFile(path = prostatePath, destination_frame = "prostate.hex")
#' h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex,
#'         family = "binomial", nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#'
#' # Run GLM of VOL ~ CAPSULE + AGE + RACE + PSA + GLEASON
#' myX = setdiff(colnames(prostate.hex), c("ID", "DPROS", "DCAPS", "VOL"))
#' h2o.glm(y = "VOL", x = myX, training_frame = prostate.hex, family = "gaussian",
#'         nfolds = 0, alpha = 0.1, lambda_search = FALSE)
#'
#'
#' # GLM variable importance
#' # Also see:
#' #   https://github.com/h2oai/h2o/blob/master/R/tests/testdir_demos/runit_demo_VI_all_algos.R
#' data.hex = h2o.importFile(
#'   path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv",
#'   destination_frame = "data.hex")
#' myX = 1:20
#' myY="y"
#' my.glm = h2o.glm(x=myX, y=myY, training_frame=data.hex, family="binomial", standardize=TRUE,
#'                  lambda_search=TRUE)
#' }
#' @export
h2o.glm <- function(x, y, training_frame, model_id, 
                    validation_frame = NULL,
                    ignore_const_cols = TRUE,
                    max_iterations = 50,
                    beta_epsilon = 0,
                    solver = c("IRLSM", "L_BFGS"),
                    standardize = TRUE,
                    family = c("gaussian", "binomial", "poisson", "gamma", "tweedie","multinomial"),
                    link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"),
                    tweedie_variance_power = NaN,
                    tweedie_link_power = NaN,
                    alpha = 0.5,
                    prior = 0.0,
                    lambda = 1e-05,
                    lambda_search = FALSE,
                    nlambdas = -1,
                    lambda_min_ratio = -1.0,
                    nfolds = 0,
                    fold_column = NULL,
                    fold_assignment = c("AUTO","Random","Modulo"),
                    keep_cross_validation_predictions = FALSE,
                    beta_constraints = NULL,
                    offset_column = NULL,
                    weights_column = NULL,
                    intercept = TRUE,
                    max_active_predictors = -1,
                    objective_epsilon = -1,
                    gradient_epsilon = -1,
                    non_negative = FALSE,
                    compute_p_values = FALSE,
                    remove_collinear_columns = FALSE,
                    max_runtime_secs = 0,
                    missing_values_handling = c("Skip", "MeanImputation"))
{
  # if (!is.null(beta_constraints)) {
  #     if (!inherits(beta_constraints, "data.frame") && !is.H2OFrame(beta_constraints))
  #       stop(paste("`beta_constraints` must be an H2OH2OFrame or R data.frame. Got: ", class(beta_constraints)))
  #     if (inherits(beta_constraints, "data.frame")) {
  #       beta_constraints <- as.h2o(beta_constraints)
  #     }
  # }
  if (inherits(beta_constraints, "data.frame")) {
        beta_constraints <- as.h2o(beta_constraints)
  }

  if (!is.H2OFrame(training_frame))
   tryCatch(training_frame <- h2o.getFrame(training_frame),
            error = function(err) {
              stop("argument \"training_frame\" must be a valid H2OFrame or ID")
            })

  # Parameter list to send to model builder
  parms <- list()
  parms$training_frame <- training_frame
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
  if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  if( !missing(validation_frame) )          parms$validation_frame       <- validation_frame
  if( !missing(model_id) )                  parms$model_id               <- model_id
  if( !missing(ignore_const_cols)  )        parms$ignore_const_cols      <- ignore_const_cols
  if( !missing(max_iterations) )            parms$max_iterations         <- max_iterations
  if( !missing(beta_epsilon) )              parms$beta_epsilon           <- beta_epsilon
  if( !missing(solver) )                    parms$solver                 <- solver
  if( !missing(standardize) )               parms$standardize            <- standardize
  if( !missing(family) )                    parms$family                 <- family
  if( !missing(link) )                      parms$link                   <- link
  if( !missing(tweedie_variance_power) )    parms$tweedie_variance_power <- tweedie_variance_power
  if( !missing(tweedie_link_power) )        parms$tweedie_link_power     <- tweedie_link_power
  if( !missing(alpha) )                     parms$alpha                  <- alpha
  if( !missing(prior) )                     parms$prior                  <- prior
  if( !missing(lambda) )                    parms$lambda                 <- lambda
  if( !missing(lambda_search) )             parms$lambda_search          <- lambda_search
  if( !missing(nlambdas) )                  parms$nlambdas               <- nlambdas
  if( !missing(lambda_min_ratio) )          parms$lambda_min_ratio       <- lambda_min_ratio
  if( !missing(offset_column) )             parms$offset_column          <- offset_column
  if( !missing(weights_column) )            parms$weights_column         <- weights_column
  if( !missing(intercept) )                 parms$intercept              <- intercept
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  if( !missing(max_active_predictors) )     parms$max_active_predictors  <- max_active_predictors
  if( !missing(objective_epsilon) )         parms$objective_epsilon      <- objective_epsilon
  if( !missing(gradient_epsilon) )          parms$gradient_epsilon       <- gradient_epsilon
  if( !missing(non_negative) )              parms$non_negative           <- non_negative
  if( !missing(compute_p_values) )          parms$compute_p_values       <- compute_p_values
  if( !missing(remove_collinear_columns) )  parms$remove_collinear_columns<- remove_collinear_columns
  if( !missing(max_runtime_secs))           parms$max_runtime_secs       <- max_runtime_secs
  # For now, accept nfolds in the R interface if it is 0 or 1, since those values really mean do nothing.
  # For any other value, error out.
  # Expunge nfolds from the message sent to H2O, since H2O doesn't understand it.
  if (!missing(nfolds) && nfolds > 1)
    parms$nfolds <- nfolds
  if(!missing(beta_constraints))
    parms$beta_constraints <- beta_constraints
    if(!missing(missing_values_handling))
        parms$missing_values_handling <- missing_values_handling
  m <- .h2o.modelJob('glm', parms)
  m@model$coefficients <- m@model$coefficients_table[,2]
  names(m@model$coefficients) <- m@model$coefficients_table[,1]
  m
}

#' Set betas of an existing H2O GLM Model
#'
#' This function allows setting betas of an existing glm model.
#' @param model an \linkS4class{H2OModel} corresponding from a \code{h2o.glm} call.
#' @param beta a new set of betas (a named vector)
#' @export
h2o.makeGLMModel <- function(model,beta) {
   res = .h2o.__remoteSend(method="POST", .h2o.__GLMMakeModel, model=model@model_id, names = paste("[",paste(paste("\"",names(beta),"\"",sep=""), collapse=","),"]",sep=""), beta = paste("[",paste(as.vector(beta),collapse=","),"]",sep=""))
   m <- h2o.getModel(model_id=res$model_id$name)
   m@model$coefficients <- m@model$coefficients_table[,2]
   names(m@model$coefficients) <- m@model$coefficients_table[,1]
   m
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
#    parms$training_frame  = training_frame
#    parms$beta_constraints = beta_constraints
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
