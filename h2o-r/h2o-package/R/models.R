#'
#' Retrieve Model Data
#'
#' After a model is constructed by H2O, R must create a view of the model. All views are backed by S4 objects that
#' subclass the H2OModel object (see classes.R for class specifications).
#'
#' This file contains the set of model getters that fill out and return the appropriate S4 object.
#'
#'
#' Maintenance strategy:
#'
#'   The getter code attempts to be as modular and concisse as possible. The overall strategy is to create, for each
#'   model type, a list of parameters to be filled in by the retrieved json. There is a mapping from the json names
#'   to the names used in the model (e.g. see .json.to.R.map). This is used to perform succinct model data filling.


#'
#' Model Parameter Mapping
#'
#' This is the JSON to R mapping of model-specific result and parameter names.
.json.to.R.map <- list(
  glm = list(
    iteration         = "iter",
    beta              = "coefficients",
    norm_beta         = "normalized_coefficients",
#    lambda_value      = "lambda",
    null_deviance     = "null.deviance",
    residual_deviance = "deviance",
    avg_err           = "train.err"),

  gbm = list(),          #TODO
  pca = list(),          #TODO
  deeplearning = list(), #TODO
  drf = list(),          #TODO
  speedrf = list(),      #TODO
  kmeans = list(),       #TODO
  nb = list(),           #TODO
  perf = list()          #TODO
)

#.R.model.fields <- list(
#  glm.result.fields   = c("coefficients","normalized_coefficients","rank","iter","lambda","deviance",
#                          "null.deviance","df.residual","df.null","aic","train.err"),
#  glm.binomial.result = c(glm.result.fields, "prior", "threshold", "best_threshold", "auc", "confusion")
#)

#'
#' Fetch the JSON for a given model key.
#'
#' Grabs all of the JSON and returns it as a named list. Do this by using the 2/Inspector.json page, which provides
#' a redirect URL to the appropriate Model View page.
.h2o.__fetch.JSON<-
function(h2o, key, page = "") {
  if (page == "") {
    redirect_url <- .h2o.__remoteSend(h2o, .h2o.__PAGE_INSPECTOR, src_key = key)$response_info$redirect_url
    page <- strsplit(redirect_url, '\\?')[[1]][1]                         # returns a list of two items
    page <- paste(strsplit(page, '')[[1]][-1], sep = "", collapse = "")   # strip off the leading '/'
    key  <- strsplit(strsplit(redirect_url, '\\?')[[1]][2], '=')[[1]][2]  # split the second item into a list of two items
    if (grepl("GLMGrid", page)) .h2o.__remoteSend(client = h2o, page = page, grid_key = key) #glm grid page
    else .h2o.__remoteSend(client = h2o, page = page, '_modelKey' = key)
  } else {
    if (grepl("GLMGrid", page)) .h2o.__remoteSend(client = h2o, page = page, grid_key = key) #glm grid page
    else .h2o.__remoteSend(client = h2o, page = page, '_modelKey' = key)
  }
}


#'
#' Fetch the parameters of the model.
#'
#' A helper function to retrieve the parameters of the model from the model view page.
.get.model.params<-
function(h2o, key, page = "") {
  json <- .h2o.__fetch.JSON(h2o, key, page = page)
  algo <- model.type <- names(json)[3]
  if (algo == "grid") return("") # no parameters if algo.type is "grid" --> GLMGRrid result
  params <- json[[model.type]]$parameters
  params$h2o <- h2o
  params
}

#'
#' Helper to filter out non-null results
.filt <- function(l) names(Filter(is.null, l))

#'
#' Loop over the list of json lumps to fill `to` in with.
.fill.results<-
function(to, ...) {
  # handy-dandy, old-fashioned for loop needed
  for (l in list(...)) to[.filt(to)] <- l[.filt(to)]
  to
}

#'
#' Helper to recursively replace leading '_' in list names.
.repl <- function(l) { names(l) <- unlist(lapply(names(l), function(x) gsub("^_*", "", x))); l }

#'
#' Helper to recursively map json names to model names
.names.mapper<-
function(l, model.names) {
  a <- match(names(model.names), names(l))
  a <- a[!is.na(a)]
  names(l)[a] <- unlist(lapply(names(l)[a], function(n) model.names[[n]]))
  l
}

#'
#' Helper to recursively operate on a list.
.rlapply<-
function(l, func, ...) {
  if (is.list(l)) {
    l <- func(l, ...)
    l <- lapply(l, .rlapply, func, ...)
  }
  l
}

#'
#' Preamble method for every model.
#'
#' Fetches all of the json and the parameters. Maps JSON field names to R names with the model.names argument.
.h2o.__model.preamble<-
function(h2o, key, model.names = "", page = "") {
  params <- .get.model.params(h2o, key, page)
  json   <- .h2o.__fetch.JSON(h2o, key, page)
  res <- list(json = json, params = params)
  res <- .rlapply(res, .repl)
  if ( length(model.names) > 1) res <- .rlapply(res, .names.mapper, model.names)
  res
}

#-----------------------------------------------------------------------------------------------------------------------
#
#       GLM Model Getting
#
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Field names for GLM results
.glm.result.fields   <- c("coefficients","normalized_coefficients","rank","iter","lambda","deviance",
                          "null.deviance","df.residual","df.null","aic","train.err")
.glm.binomial.result <- c(.glm.result.fields, "prior", "threshold", "best_threshold", "auc", "confusion")
.glm.summary         <- c("model_key", "alpha", "lambda_min", "lambda_max", "lambda_best", "iterations", "aic",
                          "dev_explained")
.glm.binomial.summary<- c(.glm.summary, "auc")

#'
#' Is the GLM family binomial?
.isBinomial <- function(j) j$params$family == "binomial"

#'
#' Is the GLM family tweedie?
.isTweedie  <- function(j) j$params$family == "tweedie"

#'
#' Fill in the xvalidation results if there are any
.fill.xvals<-
function(submod, h2o, lambda_idx, return_all_lambda) {
  res_xval <- list()
  if (!is.null(submod$xvalidation)) {
    xvalKeys <- submod$xvalidation$xval_models  # fill in the xvalidation results
    if (!is.null(xvalKeys) && length(xvalKeys) >= 2) {
      res_xval <- lapply(xvalKeys, function(key, h2o, idx, ret_all) { .h2o.__getGLMResults(h2o, key, idx, ret_all) }, h2o, lambda_idx, return_all_lambda)
    }
  }
  res_xval
}

#'
#' Fill in a single GLM Result
.h2o.__getGLMResults<-
function(h2o, key, lambda_idx = -1, return_all_lambda = TRUE, pre = "", data = NULL) {
  pre    <- if(length(pre) <= 1) .h2o.__model.preamble(h2o, key, .json.to.R.map$glm, page = .h2o.__PAGE_GLMModelView) else pre
  params <- pre$params
  if (grepl("xval", pre$json$glm_model$dataKey) || is.null(data)) data <- h2o.getFrame(h2o, pre$json$glm_model$dataKey)
  if (lambda_idx == -1 || !return_all_lambda) lambda_idx <- pre$json$glm_model$best_lambda_idx+1
  submod <- pre$json$glm_model$submodels[[lambda_idx]]
  valid  <- if(is.null(submod$xvalidation)) submod$validation else submod$xvalidation
  # create an empty list of results that will be filled in below
  result <- sapply(if(.isBinomial(pre)) .glm.binomial.result else .glm.result.fields, function(x) {})
  # fill in the results
  result             <- .fill.results(result, submod, pre$json, pre$json$glm_model, valid)
  result$lambda      <- submod$lambda_value
  result$df.residual <- max(valid$nobs-result$rank,0)  # post processing!
  result$df.null     <- valid$nobs-1                   # post processing!
  idxes <- submod$idxs + 1
  names(result$coefficients) <- pre$json$glm_model$coefficients_names[idxes]
  if (!is.null(result$normalized_coefficients))
    names(result$normalized_coefficients) <- pre$json$glm_model$coefficients_names[idxes]
  if(.isBinomial(pre)) {  # build and set the confusion matrix
    cm_ind <- trunc(100*result$best_threshold) + 1
    result$confusion <- .build_cm(valid$cms[[cm_ind]]$arr, c("false", "true"))
  }
  # fill in the params
  params$lambda_all  <- sapply(pre$json$glm_model$submodels, function(x) { x$lambda_value })
  params$lambda_best <- params$lambda_all[[pre$json$glm_model$best_lambda_idx+1]]
  if (.isTweedie(pre))
    params$family <- .h2o.__getFamily(params$family, params$link, params$tweedie_variance_power, params$tweedie_link_power)
  else
    params$family <- .h2o.__getFamily(params$family, params$link)
  result$params <- params                                                         # Fill in the parameters
  res_xval      <- .fill.xvals(submod, h2o, lambda_idx, return_all_lambda)        # Fill in the xvalidations
  new("H2OGLMModel", key = key, data = data, model = result, xval = res_xval)     # Return a new GLMModel
}

#'
#' Return all lambdas case
.get.all.glm.models<-
function(pre, h2o, key, num_lambda, best_lambda_idx, data) {
  models <- lapply(1:num_lambda, function(idx, h2o, key, pre, data) {.h2o.__getGLMResults(h2o, key, idx, TRUE, pre, data)}, h2o, key, pre, data)
  lambdas <- unlist(lapply(models, function(m) m@model$lambda))
  new("H2OGLMModelList", models = models, best_model = best_lambda_idx, lambdas = lambdas)
}

#'
#' Top-level call for retrieving GLM results.
#'
#' Here is where it's decided whether or not to retrieve all lambdas.
.h2o.get.glm<-
function(h2o, key, return_all_lambda = TRUE) {
  pre <- .h2o.__model.preamble(h2o, key, .json.to.R.map$glm)
  if(!is.null(pre$json$glm_model$warnings))
      invisible(lapply(pre$json$glm_model$warnings, warning))
  submodels <- pre$json$glm_model$submodels
  best_lambda_idx <- pre$json$glm_model$best_lambda_idx+1
  data <- h2o.getFrame(h2o, pre$json$glm_model$dataKey)
  if (!return_all_lambda || length(submodels) == 1) .h2o.__getGLMResults(h2o, key, best_lambda_idx, FALSE, "", data)
  else .get.all.glm.models(pre, h2o, key, length(submodels), best_lambda_idx, data)
}


#'
#' Top-level call for retrieving GLM Grid Results
#'
#' Gather up the GLM Grid results
.h2o.get.glm.grid<-
function(h2o, key, return_all_lambda = TRUE, data) {
  grid.pre <- .h2o.__model.preamble(h2o, key, "", .h2o.__PAGE_GLM2GridView)
  modelKeys <- grid.pre$json$grid$destination_keys
  models <- list(); modelSummaries <- list()
  for (i in 1:length(modelKeys)) {
    models[[i]] <- .h2o.get.glm(h2o, as.character(modelKeys[i]), return_all_lambda)
    modelSummaries[[i]] <- .h2o.__getGLMSummary(models[[i]])
  }
  new("H2OGLMGrid", key = key, data = data, model = models, sumtable = modelSummaries)
}

#'
#' Construct a summary of the GLM.
.h2o.__getGLMSummary<-
function(model) {
  if (is(model, "H2OGLMModelList")) model <- model@models[[model@best_model]]
  result <- list()
  result$model_key     <- model@key
  result$alpha         <- model@model$params$alpha
  result$lambda_min    <- min(model@model$params$lambda_all)
  result$lambda_max    <- max(model@model$params$lambda_all)
  result$lambda_best   <- model@model$params$lambda_best
  result$iterations    <- model@model$iter
  result$aic           <- model@model$aic
  result$auc           <- model@model$auc
  result$dev_explained <- 1 - (model@model$deviance / model@model$null.deviance)
  result
}

.h2o.__getFamily <- function(family, link, tweedie.var.p = 0, tweedie.link.p = 1-tweedie.var.p) {
  if(family == "tweedie")
    return(tweedie(var.power = tweedie.var.p, link.power = tweedie.link.p))

  if(missing(link)) {
    switch(family,
           "gaussian" = gaussian(),
           "binomial" = binomial(),
           "poisson" = poisson(),
           "gamma" = Gamma())
  } else {
    switch(family,
           "gaussian" = gaussian(link),
           "binomial" = binomial(link),
           "poisson" = poisson(link),
           "gamma" = Gamma(link))
  }
}

#-----------------------------------------------------------------------------------------------------------------------
#
#       KMeans Model Getter
#
#-----------------------------------------------------------------------------------------------------------------------

.kmeans.builder <- function(json, client) {
  if(NCOL(json$output$centers) == length(json$output$names))
    colnames(json$output$centers) <- json$output$names
  new("H2OKMeansModel", h2o = client, key = json$key$name, model = json$output,
      valid = new("H2OFrame", h2o = client, key="NA"))
}

#-----------------------------------------------------------------------------------------------------------------------
#
#       GBM Model Getter
#
#-----------------------------------------------------------------------------------------------------------------------

.gbm.builder <- function(json, client) {
  new("H2OGBMModel", h2o = client, key = json$key$name, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.glm.builder <- function(json, client) {
  new("H2OGLMModel", h2o = client, key = json$key$name, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.deeplearning.builder <- function(json, client) {
  new("H2ODeepLearningModel", h2o = client, key = json$key$name, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.quantile.builder <- function(json, client) {
  new("H2OQuantileModel", h2o = client, key = json$key$name, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}