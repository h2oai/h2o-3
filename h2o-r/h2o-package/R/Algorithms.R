# Model-building operations and algorithms
# ----------------------- Generalized Boosting Machines (GBM) ----------------------- #
# TODO: don't support missing x; default to everything?
h2o.gbm <- function(x, y, distribution = 'multinomial', data, n.trees = 10, interaction.depth = 5, n.minobsinnode = 10, shrinkage = 0.1,
    n.bins = 100, importance = FALSE, validation, balance.classes=FALSE, max.after.balance.size=5) {
  args <- .verify_dataxy(data, x, y)

  if(!is.numeric(n.trees)) stop('n.trees must be numeric')
  if( any(n.trees < 1) ) stop('n.trees must be >= 1')
  if(!is.numeric(interaction.depth)) stop('interaction.depth must be numeric')
  if( any(interaction.depth < 1) ) stop('interaction.depth must be >= 1')
  if(!is.numeric(n.minobsinnode)) stop('n.minobsinnode must be numeric')
  if( any(n.minobsinnode < 1) ) stop('n.minobsinnode must be >= 1')
  if(!is.numeric(shrinkage)) stop('shrinkage must be numeric')
  if( any(shrinkage < 0 | shrinkage > 1) ) stop('shrinkage must be in [0,1]')
  if(!is.numeric(n.bins)) stop('n.bins must be numeric')
  if(any(n.bins < 1)) stop('n.bins must be >= 1')
  if(!is.logical(importance)) stop('importance must be logical (TRUE or FALSE)')

  if(missing(validation)) validation = data
  # else if(class(validation) != "H2OParsedData") stop("validation must be an H2O dataset")
  else if(!class(validation) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("validation must be an H2O parsed dataset")

  if(!(distribution %in% c('multinomial', 'gaussian', 'bernoulli')))
    stop(paste(distribution, "is not a valid distribution; only [multinomial, gaussian, bernoulli] are supported"))
  classification <- ifelse(distribution %in% c('multinomial', 'bernoulli'), 1, ifelse(distribution=='gaussian', 0, -1))

  if(!is.logical(balance.classes)) stop('balance.classes must be logical (TRUE or FALSE)')
  if(!is.numeric(max.after.balance.size)) stop('max.after.balance.size must be a number')
  if( any(max.after.balance.size <= 0) ) stop('max.after.balance.size must be >= 0')
  if(balance.classes && !classification) stop('balance.classes can only be used for classification')

  # NB: externally, 1 based indexing; internally, 0 based
  cols = paste(args$x_i - 1, collapse=",")
  if(distribution == "bernoulli")
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GBM, source=data@key, response=args$y, cols=cols, ntrees=n.trees, max_depth=interaction.depth, learn_rate=shrinkage, family = "bernoulli",
      min_rows=n.minobsinnode, classification=classification, nbins=n.bins, importance=as.numeric(importance), validation=validation@key, balance_classes=as.numeric(balance.classes), max_after_balance_size=as.numeric(max.after.balance.size))
  else
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GBM, source=data@key, response=args$y, cols=cols, ntrees=n.trees, max_depth=interaction.depth, learn_rate=shrinkage,
      min_rows=n.minobsinnode, classification=classification, nbins=n.bins, importance=as.numeric(importance), validation=validation@key, balance_classes=as.numeric(balance.classes), max_after_balance_size=as.numeric(max.after.balance.size))
  params = list(x=args$x, y=args$y, distribution=distribution, n.trees=n.trees, interaction.depth=interaction.depth, shrinkage=shrinkage, n.minobsinnode=n.minobsinnode, n.bins=n.bins, importance=importance, balance.classes=balance.classes, max.after.balance.size=max.after.balance.size)

  if(length(n.trees) == 1 && length(interaction.depth) == 1 && length(n.minobsinnode) == 1 && length(shrinkage) == 1 && length(n.bins) == 1 && length(max.after.balance.size) == 1) {
    .h2o.__waitOnJob(data@h2o, res$job_key)
    # while(!.h2o.__isDone(data@h2o, "GBM", res)) { Sys.sleep(1) }
    res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GBMModelView, '_modelKey'=res$destination_key)
    result = .h2o.__getGBMResults(res2$gbm_model, params)
    new("H2OGBMModel", key=res$destination_key, data=data, model=result, valid=validation)
  } else {
    # .h2o.gridsearch.internal("GBM", data, res$job_key, res$destination_key, validation, params)
    .h2o.gridsearch.internal("GBM", data, res, validation, params)
  }
}

.h2o.__getGBMSummary <- function(res, params) {
  mySum = list()
  mySum$model_key = res$'_key'
  mySum$ntrees = res$N
  mySum$max_depth = res$max_depth
  mySum$min_rows = res$min_rows
  mySum$nbins = res$nbins
  mySum$learn_rate = res$learn_rate
  mySum$balance_classes = res$balance_classes
  mySum$max_after_balance_size = res$max_after_balance_size

  # if(params$distribution == "multinomial") {
    # temp = matrix(unlist(res$cm), nrow = length(res$cm))
    # mySum$prediction_error = 1-sum(diag(temp))/sum(temp)
    # mySum$prediction_error = tail(res$'cms', 1)[[1]]$'_predErr'
  # }
  return(mySum)
}

.h2o.__getGBMResults <- function(res, params) {
  if(res$parameters$state == "CRASHED")
    stop(res$parameters$exception)
  
  result = list()
  params$n.trees = res$N
  params$interaction.depth = res$max_depth
  params$n.minobsinnode = res$min_rows
  params$shrinkage = res$learn_rate
  params$n.bins = res$nbins
  result$params = params
  params$balance.classes = res$balance_classes
  params$max.after.balance.size = res$max_after_balance_size

  if(result$params$distribution %in% c("multinomial", "bernoulli")) {
    class_names = res$'cmDomain' # tail(res$'_domains', 1)[[1]]
    result$confusion = .build_cm(tail(res$'cms', 1)[[1]]$'_arr', class_names)  # res$'_domains'[[length(res$'_domains')]])
    result$classification <- T

    if(!is.null(res$validAUC)) {
      tmp <- .h2o.__getPerfResults(res$validAUC)
      tmp$confusion <- NULL
      result <- c(result, tmp)
    }
  } else
    result$classification <- F
  
  if(params$importance) {
    result$varimp = data.frame(res$varimp$varimp)
    result$varimp[,2] = result$varimp[,1]/max(result$varimp[,1])
    result$varimp[,3] = 100*result$varimp[,1]/sum(result$varimp[,1])
    rownames(result$varimp) = res$'_names'[-length(res$'_names')]
    colnames(result$varimp) = c(res$varimp$method, "Scaled.Values", "Percent.Influence")
    result$varimp = result$varimp[order(result$varimp[,1], decreasing = TRUE),]
  }

  result$err = as.numeric(res$errs)
  return(result)
}

# -------------------------- Generalized Linear Models (GLM) ------------------------ #
h2o.glm <- function(x, y, data, family, nfolds = 10, alpha = 0.5, lambda = 1e-5, epsilon = 1e-4, standardize = TRUE, prior, tweedie.p = ifelse(family == 'tweedie', 1.5, as.numeric(NA)), thresholds, iter.max, higher_accuracy, lambda_search, version = 2) {
  if(version == 1) {
    if(missing(thresholds))
      thresholds = ifelse(family=='binomial', seq(0, 1, 0.01), as.numeric(NA))
    if(!missing(iter.max)) stop("iter.max not supported under ValueArray")
    if(!missing(higher_accuracy)) stop("line search not supported under ValueArray")
    if(!missing(lambda_search)) stop("automated search over lambda not supported under ValueArray")
    h2o.glm.VA(x, y, data, family, nfolds, alpha, lambda, epsilon, standardize, prior, tweedie.p, thresholds)
  } else if(version == 2) {
    if(!missing(thresholds)) stop("thresholds not supported under FluidVecs")
    if(missing(iter.max)) iter.max = 100
    if(missing(higher_accuracy)) higher_accuracy = FALSE
    if(missing(lambda_search)) lambda_search = FALSE
    h2o.glm.FV(x, y, data, family, nfolds, alpha, lambda, epsilon, standardize, prior, tweedie.p, iter.max, higher_accuracy, lambda_search)
  } else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

# --------------------------------- ValueArray -------------------------------------- #
h2o.glm.VA <- function(x, y, data, family, nfolds = 10, alpha = 0.5, lambda = 1e-5, epsilon = 1e-4, standardize = TRUE, prior, tweedie.p = ifelse(family=='tweedie', 1.5, as.numeric(NA)), thresholds = ifelse(family=='binomial', seq(0, 1, 0.01), as.numeric(NA))) {
  if(class(data) != "H2OParsedDataVA")
    stop("data must be of class H2OParsedDataVA. Please import data via h2o.importFile.VA or h2o.importFolder.VA")
  args <- .verify_dataxy(data, x, y)

  if(missing(family) || !family %in% c("gaussian", "binomial", "poisson", "gamma", "tweedie"))
    stop("family must be one of gaussian, binomial, poisson, gamma, and tweedie")
  if( !is.numeric(nfolds) ) stop('nfolds must be numeric')
  if( nfolds < 0 ) stop('nfolds must be >= 0')
  if(!is.numeric(alpha)) stop("alpha must be numeric")
  if( any(alpha < 0) ) stop('alpha must be >= 0')
  if(!is.numeric(lambda)) stop("lambda must be numeric")
  if( any(lambda < 0) ) stop('lambda must be >= 0')
  if(!is.numeric(epsilon)) stop("epsilon must be numeric")
  if( epsilon < 0 ) stop('epsilon must be >= 0')
  if( !is.logical(standardize) ) stop('standardize must be logical (TRUE or FALSE)')
  if(!missing(prior)) {
    if(!is.numeric(prior)) stop("prior must be numeric")
    if(prior < 0 || prior > 1) stop("prior must be in [0,1]")
    if(family != "binomial") stop("prior may only be set for family binomial")
  }
  if( !is.numeric(tweedie.p) ) stop('tweedie.p must be numeric')
  if(!is.numeric(thresholds)) stop("thresholds must be numeric")
  if(family != "binomial" && !(missing(thresholds) || is.na(thresholds)))
    stop("thresholds may only be set for family binomial")
  
  # NB: externally, 1 based indexing; internally, 0 based
  if((missing(lambda) || length(lambda) == 1) && (missing(alpha) || length(alpha) == 1))
    .h2o.glm.internal(args$x_i - 1, args$y, data, family, nfolds, alpha, lambda, 1, epsilon, standardize, prior, tweedie.p, thresholds)
  else {
    if(!missing(prior)) print("prior not available in GLM grid search under ValueArray")
    if(!missing(tweedie.p) && !is.na(tweedie.p)) print('tweedie variance power not available in GLM grid search under ValueArray')
    .h2o.glmgrid.internal(args$x_i - 1, args$y, data, family, nfolds, alpha, lambda, epsilon, standardize, thresholds)
  }
}

.h2o.glm.internal <- function(x, y, data, family, nfolds, alpha, lambda, expert_settings, beta_epsilon, standardize, prior, tweedie.p, thresholds) {
  if(family == 'tweedie' && (tweedie.p < 1 || tweedie.p > 2 )) stop('tweedie.p must be in (1,2)')
  if(family != "tweedie" && !(missing(tweedie.p) || is.na(tweedie.p) ) ) stop('tweedie.p may only be set for family tweedie')
  if(family != "binomial" && !missing(prior)) stop('prior may only be set for family binomial')

  thres = .seq_to_string(thresholds)
  if(family == 'tweedie')
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM, key=data@key, y=y, x=paste(x, sep="", collapse=","), family=family, n_folds=nfolds, alpha=alpha, lambda=lambda, expert_settings=expert_settings, beta_epsilon=beta_epsilon, standardize=as.numeric(standardize), case_mode="=", case=1.0, tweedie_power=tweedie.p, thresholds=thres)
  else if(family == "binomial") {
    if(missing(prior)) prior = -1
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM, key=data@key, y=y, x=paste(x, sep="", collapse=","), family=family, n_folds=nfolds, alpha=alpha, lambda=lambda, expert_settings=expert_settings, beta_epsilon=beta_epsilon, standardize=as.numeric(standardize), case_mode="=", case=1.0, prior=prior, thresholds=thres)
  } else
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM, key=data@key, y=y, x=paste(x, sep="", collapse=","), family=family, n_folds=nfolds, alpha=alpha, lambda=lambda, expert_settings=expert_settings, beta_epsilon=beta_epsilon, standardize=as.numeric(standardize), case_mode="=", case=1.0, thresholds=thres)
  params = list(x=x, y=y, family=.h2o.__getFamily(family, tweedie.var.p=tweedie.p), nfolds=nfolds, alpha=alpha, lambda=lambda, beta_epsilon=beta_epsilon, standardize=standardize, thresholds=thresholds)

  destKey = res$destination_key
  .h2o.__waitOnJob(data@h2o, res$response$redirect_request_args$job)
  # while(!.h2o.__isDone(data@h2o, "GLM1", res)) { Sys.sleep(1) }
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_INSPECT, key=destKey)
  resModel = res2$GLMModel

  # Check for any warnings
  if(!is.null(resModel$warnings) && length(resModel$warnings) > 0) {
    for(i in 1:length(resModel$warnings))
      warning(resModel$warnings[[i]])
  }
  modelOrig = .h2o.__getGLMResults(resModel, params)

  # Get results from cross-validation
  if(nfolds < 2)
    return(new("H2OGLMModelVA", key=destKey, data=data, model=modelOrig, xval=list()))

  res_xval = list()
  for(i in 1:nfolds) {
    xvalKey = resModel$validations[[1]]$xval_models[i]
    resX = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_INSPECT, key=xvalKey)
    modelXval = .h2o.__getGLMResults(resX$GLMModel, params)
    res_xval[[i]] = new("H2OGLMModelVA", key=xvalKey, data=data, model=modelXval, xval=list())
  }
  new("H2OGLMModelVA", key=destKey, data=data, model=modelOrig, xval=res_xval)
}

.h2o.glmgrid.internal <- function(x, y, data, family, nfolds, alpha, lambda, epsilon, standardize, thresholds) {
  thres = .seq_to_string(thresholds)
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLMGrid, key = data@key, y = y, x = paste(x, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_eps = epsilon, standardize = as.numeric(standardize), case_mode="=", case=1.0, parallel=1, thresholds=thres)
  params = list(x=x, y=y, family=.h2o.__getFamily(family), nfolds=nfolds, alpha=alpha, lambda=lambda, beta_epsilon=epsilon, standardize=standardize, thresholds=thresholds)

  destKey = res$destination_key
  .h2o.__waitOnJob(data@h2o, res$response$redirect_request_args$job)
  # while(!.h2o.__isDone(data@h2o, "GLM1Grid", res)) { Sys.sleep(1) }
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLMGridProgress, destination_key=destKey)
  allModels = res2$models

  result = list()
  # tweedie.p = as.numeric(NA)
  # result$Summary = t(sapply(res$models,c))
  for(i in 1:length(allModels)) {
    resH = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_INSPECT, key=allModels[[i]]$key)

    # Check for any warnings
    if(!is.null(resH$GLMModel$warnings) && length(resH$GLMModel$warnings) > 0) {
      for(j in 1:length(resH$GLMModel$warnings))
        warning("Model ", allModels[[i]]$key, ": ", resH$GLMModel$warnings[[j]])
    }
    modelOrig = .h2o.__getGLMResults(resH$GLMModel, params)

    if(nfolds < 2)
      result[[i]] = new("H2OGLMModelVA", key=allModels[[i]]$key, data=data, model=modelOrig, xval=list())
    else {
      res_xval = list()
      for(j in 1:nfolds) {
        xvalKey = resH$GLMModel$validations[[1]]$xval_models[j]
        resX = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_INSPECT, key=xvalKey)
        modelXval = .h2o.__getGLMResults(resX$GLMModel, params)
        res_xval[[j]] = new("H2OGLMModelVA", key=xvalKey, data=data, model=modelXval, xval=list())
      }
      result[[i]] = new("H2OGLMModelVA", key=allModels[[i]]$key, data=data, model=modelOrig, xval=res_xval)
    }
  }
  new("H2OGLMGridVA", key=destKey, data=data, model=result, sumtable=allModels)
}

# Pretty formatting of H2O GLM results
.h2o.__getGLMResults <- function(res, params) {
  result = list()
  params$lambda = res$LSMParams$lambda
  params$alpha = res$LSMParams$alpha
  result$params = params

  result$coefficients = unlist(res$coefficients)
  if(result$params$standardize)
    result$normalized_coefficients = unlist(res$normalized_coefficients)
  result$rank = res$nCols
  # result$family = .h2o.__getFamily(family, tweedie.var.p = tweedie.p)
  result$deviance = as.numeric(res$validations[[1]]$resDev)
  result$aic = as.numeric(res$validations[[1]]$aic)
  result$null.deviance = as.numeric(res$validations[[1]]$nullDev)
  result$iter = res$iterations
  result$df.residual = res$dof
  result$df.null = res$nLines - 1
  result$train.err = as.numeric(res$validations[[1]]$err)
  # result$y = y
  # result$x = res$column_names
  # result$tweedie.p = ifelse(missing(tweedie.p), 'NA', tweedie.p)

  if(result$params$family$family == "binomial") {
    result$auc = as.numeric(res$validations[[1]]$auc)
    result$threshold = as.numeric(res$validations[[1]]$threshold)
    result$class.err = res$validations[[1]]$classErr

    # Construct confusion matrix
    temp = t(data.frame(sapply(res$validations[[1]]$cm, c)))
    dn = list(Actual = temp[-1,1], Predicted = temp[1,-1])
    temp = temp[-1,]; temp = temp[,-1]
    dimnames(temp) = dn
    result$confusion = temp
  }
  return(result)
}

# -------------------------- FluidVecs -------------------------- #
h2o.glm.FV <- function(x, y, data, family, nfolds = 10, alpha = 0.5, lambda = 1e-5, epsilon = 1e-4, standardize = TRUE, prior, tweedie.p = ifelse(family == "tweedie", 1.5, as.numeric(NA)), iter.max = 100, higher_accuracy = FALSE, lambda_search = FALSE) {
  args <- .verify_dataxy(data, x, y)

  if(!is.numeric(nfolds)) stop('nfolds must be numeric')
  if( nfolds < 0 ) stop('nfolds must be >= 0')
  if(!is.numeric(alpha)) stop('alpha must be numeric')
  if( any(alpha < 0) ) stop('alpha must be >= 0')
  if(!is.numeric(lambda)) stop('lambda must be numeric')
  if( any(lambda < 0) ) stop('lambda must be >= 0')
  if(!is.numeric(epsilon)) stop("epsilon must be numeric")
  if( epsilon < 0 ) stop('epsilon must be >= 0')
  if(!is.logical(standardize)) stop("standardize must be logical")
  if(!missing(prior)) {
    if(!is.numeric(prior)) stop("prior must be numeric")
    if(prior < 0 || prior > 1) stop("prior must be in [0,1]")
    if(family != "binomial") stop("prior may only be set for family binomial")
  }
  if(!is.numeric(tweedie.p)) stop('tweedie.p must be numeric')
  if( family != 'tweedie' && !(missing(tweedie.p) || is.na(tweedie.p)) ) stop("tweedie.p may only be set for family tweedie")
  if(!is.numeric(iter.max)) stop('iter.max must be numeric')
  if(!is.logical(higher_accuracy)) stop("higher_accuracy must be logical")
  if(!is.logical(lambda_search)) stop("lambda_search must be logical")
  if(lambda_search && length(lambda) > 1) stop("When automatically searching, must specify single numeric value as lambda, which is interpreted as minimum lambda in generated sequence")

  x_ignore = setdiff(1:ncol(data), c(args$x_i, args$y_i)) - 1
  if(length(x_ignore) == 0) x_ignore = ''

  if(length(alpha) == 1) {
    rand_glm_key = .h2o.__uniqID("GLM2Model")
    if(family == "tweedie")
      res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2, source = data@key, destination_key = rand_glm_key, response = args$y, ignored_cols = paste(x_ignore, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_epsilon = epsilon, standardize = as.numeric(standardize), max_iter = iter.max, higher_accuracy = as.numeric(higher_accuracy), lambda_search = as.numeric(lambda_search), tweedie_variance_power = tweedie.p)
    else if(family == "binomial") {
      if(missing(prior)) prior = -1
      res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2, source = data@key, destination_key = rand_glm_key, response = args$y, ignored_cols = paste(x_ignore, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_epsilon = epsilon, standardize = as.numeric(standardize), max_iter = iter.max, higher_accuracy = as.numeric(higher_accuracy), lambda_search = as.numeric(lambda_search), prior = prior)
    } else
      res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2, source = data@key, destination_key = rand_glm_key, response = args$y, ignored_cols = paste(x_ignore, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_epsilon = epsilon, standardize = as.numeric(standardize), max_iter = iter.max, higher_accuracy = as.numeric(higher_accuracy), lambda_search = as.numeric(lambda_search))
    params = list(x=args$x, y=args$y, family = .h2o.__getFamily(family, tweedie.var.p=tweedie.p), nfolds=nfolds, alpha=alpha, lambda=lambda, beta_epsilon=epsilon, standardize=standardize)
    .h2o.__waitOnJob(data@h2o, res$job_key)
    # while(!.h2o.__isDone(data@h2o, "GLM2", res)) { Sys.sleep(1) }

    res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLMModelView, '_modelKey'=res$destination_key)
    resModel = res2$glm_model; destKey = resModel$'_key'
    modelOrig = .h2o.__getGLM2Results(resModel, params)

    # Get results from cross-validation
    if(nfolds < 2)
      return(new("H2OGLMModel", key=destKey, data=data, model=modelOrig, xval=list()))

    res_xval = list()
    for(i in 1:nfolds) {
      xvalKey = resModel$submodels[[resModel$best_lambda_idx+1]]$validation$xval_models
      resX = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLMModelView, '_modelKey'=xvalKey[i])
      modelXval = .h2o.__getGLM2Results(resX$glm_model, params)
      res_xval[[i]] = new("H2OGLMModel", key=xvalKey[i], data=data, model=modelXval, xval=list())
    }
    new("H2OGLMModel", key=destKey, data=data, model=modelOrig, xval=res_xval)
  } else
    .h2o.glm2grid.internal(x_ignore, args$y, data, family, nfolds, alpha, lambda, epsilon, standardize, prior, tweedie.p, iter.max, higher_accuracy, lambda_search)
}

.h2o.glm2grid.internal <- function(x_ignore, y, data, family, nfolds, alpha, lambda, epsilon, standardize, prior, tweedie.p, iter.max, higher_accuracy, lambda_search) {
  if(family == "tweedie")
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2, source = data@key, response = y, ignored_cols = paste(x_ignore, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_epsilon = epsilon, standardize = as.numeric(standardize), max_iter = iter.max, higher_accuracy = as.numeric(higher_accuracy), lambda_search = as.numeric(lambda_search), tweedie_variance_power = tweedie.p)
  else if(family == "binomial") {
    if(missing(prior)) prior = -1
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2, source = data@key, response = y, ignored_cols = paste(x_ignore, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_epsilon = epsilon, standardize = as.numeric(standardize), max_iter = iter.max, higher_accuracy = as.numeric(higher_accuracy), lambda_search = as.numeric(lambda_search), prior = prior)
  }
  else
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2, source = data@key, response = y, ignored_cols = paste(x_ignore, sep="", collapse=","), family = family, n_folds = nfolds, alpha = alpha, lambda = lambda, beta_epsilon = epsilon, standardize = as.numeric(standardize), max_iter = iter.max, higher_accuracy = as.numeric(higher_accuracy), lambda_search = as.numeric(lambda_search))
  params = list(x=setdiff(colnames(data)[-(x_ignore+1)], y), y=y, family=.h2o.__getFamily(family, tweedie.var.p=tweedie.p), nfolds=nfolds, alpha=alpha, lambda=lambda, beta_epsilon=epsilon, standardize=standardize)

  .h2o.__waitOnJob(data@h2o, res$job_key)
  # while(!.h2o.__isDone(data@h2o, "GLM2", res)) { Sys.sleep(1); prog = .h2o.__poll(data@h2o, res$job_key); setTxtProgressBar(pb, prog) }

  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLM2GridView, grid_key=res$destination_key)
  destKey = res$destination_key
  allModels = res2$grid$destination_keys

  result = list(); myModelSum = list()
  for(i in 1:length(allModels)) {
    resH = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLMModelView, '_modelKey'=allModels[i])
    myModelSum[[i]] = .h2o.__getGLM2Summary(resH$glm_model)
    modelOrig = .h2o.__getGLM2Results(resH$glm_model, params)

    # Get results from cross-validation
    if(nfolds < 2)
      result[[i]] = new("H2OGLMModel", key=allModels[i], data=data, model=modelOrig, xval=list())
    else {
      res_xval = list()
      for(j in 1:nfolds) {
         xvalKey = resH$glm_model$submodels[[resH$glm_model$best_lambda_idx+1]]$validation$xval_models
         resX = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GLMModelView, '_modelKey'=xvalKey[j])
         modelXval = .h2o.__getGLM2Results(resX$glm_model, params)
         res_xval[[j]] = new("H2OGLMModel", key=xvalKey, data=data, model=modelXval, xval=list())
      }
      result[[i]] = new("H2OGLMModel", key=allModels[i], data=data, model=modelOrig, xval=res_xval)
    }
  }
  new("H2OGLMGrid", key=destKey, data=data, model=result, sumtable=myModelSum)
}

.h2o.__getGLM2Summary <- function(model) {
  mySum = list()
  mySum$model_key = model$'_key'
  mySum$alpha = model$alpha
  mySum$lambda_min = min(model$lambda)
  mySum$lambda_max = max(model$lambda)
  mySum$lambda_best = model$lambda[model$best_lambda_idx+1]

  submod = model$submodels[[model$best_lambda_idx+1]]
  mySum$iterations = submod$iteration
  valid = submod$validation

  if(model$glm$family == "binomial")
    mySum$auc = as.numeric(valid$auc)
  mySum$aic = as.numeric(valid$aic)
  mySum$dev_explained = 1-as.numeric(valid$residual_deviance)/as.numeric(valid$null_deviance)
  return(mySum)
}

# Pretty formatting of H2O GLM2 results
.h2o.__getGLM2Results <- function(model, params) {
  submod = model$submodels[[model$best_lambda_idx+1]]
  valid = submod$validation

  result = list()
  params$alpha = model$alpha
  params$lambda = model$lambdas
  params$best_lambda = model$lambdas[[model$best_lambda_idx+1]]
  result$params = params
  if(model$glm$family == "tweedie")
    result$params$family = .h2o.__getFamily(model$glm$family, model$glm$link, model$glm$tweedie_variance_power, model$glm$tweedie_link_power)
  else
    result$params$family = .h2o.__getFamily(model$glm$family, model$glm$link)
  
  result$coefficients = as.numeric(unlist(submod$beta))
  names(result$coefficients) = model$coefficients_names
  if(params$standardize) {
    result$normalized_coefficients = as.numeric(unlist(submod$norm_beta))
    names(result$normalized_coefficients) = model$coefficients_names
  }
  result$rank = valid$'_rank'
  result$iter = submod$iteration

  result$deviance = as.numeric(valid$residual_deviance)
  result$null.deviance = as.numeric(valid$null_deviance)
  result$df.residual = max(valid$nobs-result$rank,0)
  result$df.null = valid$nobs-1
  result$aic = as.numeric(valid$aic)
  result$train.err = as.numeric(valid$avg_err)

  if(model$glm$family == "binomial") {
    result$params$prior = as.numeric(model$prior)
    result$threshold = as.numeric(model$threshold)
    result$best_threshold = as.numeric(valid$best_threshold)
    result$auc = as.numeric(valid$auc)
    
    # Construct confusion matrix
    cm_ind = trunc(100*result$best_threshold) + 1
#     temp = data.frame(t(sapply(valid$'_cms'[[cm_ind]]$'_arr', c)))
#     temp[,3] = c(temp[1,2], temp[2,1])/apply(temp, 1, sum)
#     temp[3,] = c(temp[2,1], temp[1,2], 0)/apply(temp, 2, sum)
#     temp[3,3] = (temp[1,2] + temp[2,1])/valid$nobs
#     dn = list(Actual = c("false", "true", "Err"), Predicted = c("false", "true", "Err"))
#     dimnames(temp) = dn
#    result$confusion = temp
    result$confusion = .build_cm(valid$'_cms'[[cm_ind]]$'_arr', c("false", "true"))
  }
  return(result)
}

# ------------------------------ K-Means Clustering --------------------------------- #
h2o.kmeans <- function(data, centers, cols = '', iter.max = 10, normalize = FALSE, init = "none", seed = 0, dropNACols, version = 2) {
  if(version == 1) {
    if(!missing(dropNACols)) stop("dropNACols not supported under ValueArray")
    h2o.kmeans.VA(data, centers, cols, iter.max, normalize, init, seed)
  } else if(version == 2) {
    if(missing(dropNACols)) dropNACols = FALSE
    h2o.kmeans.FV(data, centers, cols, iter.max, normalize, init, seed, dropNACols)
  }
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

# -------------------------- ValueArray -------------------------- #
h2o.kmeans.VA <- function(data, centers, cols = '', iter.max = 10, normalize = FALSE, init = "none", seed = 0) {
  if(missing(data) ) stop('Must specify data')
  if(class(data) != "H2OParsedDataVA")
    stop("data must be of class H2OParsedDataVA. Please import data via h2o.importFile.VA or h2o.importFolder.VA")

  if(missing(centers)) stop('must specify centers')
  if(!is.numeric(centers) && !is.integer(centers)) stop('centers must be numeric')
  if( any(centers < 1) ) stop("centers must be an integer greater than 0")
  if(!is.numeric(iter.max)) stop('iter.max must be numeric')
  if( any(iter.max < 1)) stop('iter.max must be >= 1')
  if(!is.logical(normalize)) stop("normalize must be of class logical")
  if(length(centers) > 1 || length(iter.max) > 1) stop("K-Means grid search not supported under ValueArray")
  if(length(init) > 1 || !init %in% c("none", "plusplus", "furthest"))
    stop("init must be one of 'none', 'plusplus', or 'furthest'")
  if(!is.numeric(seed)) stop("seed must be numeric")
  
  args <- .verify_datacols(data, cols)
  myInit = switch(init, none = "None", plusplus = "PlusPlus", furthest = "Furthest")

  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_KMEANS, source_key = data@key, k = centers, max_iter = iter.max, normalize = as.numeric(normalize), cols = args$cols_ind - 1, initialization = myInit, seed = seed)
  job_key = res$response$redirect_request_args$job
  destKey = res$destination_key

  .h2o.__waitOnJob(data@h2o, job_key)
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_INSPECT, job = job_key, key = destKey)
  res2 = res2$KMeansModel

  # Organize results in a pretty format
  result = list()
  if(length(res2$clusters[[1]]) < length(args$cols))
    stop("Cannot run k-means on non-numeric columns!")

  result$params = list(cols=args$cols, centers=centers, iter.max=iter.max, normalize=normalize, init=myInit, seed=seed)
  result$centers = matrix(unlist(res2$clusters), ncol = length(args$cols), byrow = TRUE)
  dimnames(result$centers) = list(seq(1, centers), args$cols)
  result$tot.withinss = res2$error
  result$betweenss = res2$between_cluster_SS
  result$totss <- res2$total_SS
  result$cluster = h2o.predict(new("H2OKMeansModelVA", key=destKey, data=data, model=list()))

  res3 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_KMSCORE, model_key=destKey, key=data@key)
  result$size = res3$score$rows_per_cluster
  result$withinss = res3$score$sqr_error_per_cluster

  new("H2OKMeansModelVA", key=destKey, data=data, model=result)
}

# -------------------------- FluidVecs -------------------------- #
h2o.kmeans.FV <- function(data, centers, cols = '', iter.max = 10, normalize = FALSE, init = "none", seed = 0, dropNACols = FALSE) {
  args <- .verify_datacols(data, cols)
  if( missing(centers) ) stop('must specify centers')
  if(!is.numeric(centers) && !is.integer(centers)) stop('centers must be a positive integer')
  if( any(centers < 1) ) stop("centers must be an integer greater than 0")
  if(!is.numeric(iter.max)) stop('iter.max must be numeric')
  if( any(iter.max < 1)) stop('iter.max must be >= 1')
  if(!is.logical(normalize)) stop("normalize must be logical")
  if(length(init) > 1 || !init %in% c("none", "plusplus", "furthest"))
    stop("init must be one of 'none', 'plusplus', or 'furthest'")
  if(!is.numeric(seed)) stop("seed must be numeric")
  if(!is.logical(dropNACols)) stop("dropNACols must be logical")

  if(h2o.anyFactor(data[,args$cols_ind])) stop("Unimplemented: K-means can only model on numeric data")
  myInit = switch(init, none = "None", plusplus = "PlusPlus", furthest = "Furthest")

  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_KMEANS2, source=data@key, ignored_cols=args$cols_ignore, k=centers, max_iter=iter.max, normalize=as.numeric(normalize), initialization=myInit, seed=seed, drop_na_cols=as.numeric(dropNACols))
  params = list(cols=args$cols, centers=centers, iter.max=iter.max, normalize=normalize, init=myInit, seed=seed)

  if(length(centers) == 1 && length(iter.max) == 1) {
    .h2o.__waitOnJob(data@h2o, res$job_key)
    # while(!.h2o.__isDone(data@h2o, "KM", res)) { Sys.sleep(1) }
    res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_KM2ModelView, model=res$destination_key)
    res2 = res2$model

    result = .h2o.__getKM2Results(res2, data, params)
    new("H2OKMeansModel", key=res2$'_key', data=data, model=result)
  } else {
    # .h2o.gridsearch.internal("KM", data, res$job_key, res$destination_key)
    .h2o.gridsearch.internal("KM", data, res, params=params)
  }
}

.h2o.__getKM2Summary <- function(res) {
  mySum = list()
  mySum$model_key = res$'_key'
  mySum$k = res$k
  mySum$max_iter = res$iterations
  mySum$error = res$error
  return(mySum)
}

.h2o.__getKM2Results <- function(res, data, params) {
  # rand_pred_key = .h2o.__uniqID("KMeansClusters")
  # res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PREDICT2, model=res$'_key', data=data@key, prediction=rand_pred_key)
  # res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_SUMMARY2, source=rand_pred_key, cols=0)
  clusters_key <- paste(res$'_clustersKey', sep = "")

  result = list()
  params$centers = res$k
  params$iter.max = res$max_iter
  result$params = params

  result$cluster = new("H2OParsedData", h2o=data@h2o, key=clusters_key)
  feat = res$'_names'[-length(res$'_names')]     # Get rid of response column name
  result$centers = t(matrix(unlist(res$centers), ncol = res$k))
  dimnames(result$centers) = list(seq(1,res$k), feat)
  result$totss <- res$total_SS
  result$withinss <- res$within_cluster_variances
  result$tot.withinss <- res$total_within_SS
  result$betweenss <- res$between_cluster_SS
  result$size <- res$size
  result$iter <- res$iterations
  return(result)
}

.addParm <- function(parms, k, v) {
  cmd = sprintf("parms$%s = v", k)
  eval(parse(text=cmd))
  return(parms)
}

.addStringParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.character(v)) stop(sprintf("%s must be of type character"), k)
    parms = .addParm(parms, k, v)
  }
  return(parms)
}

.addBooleanParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.logical(v)) stop(sprintf("%s must be of type logical"), k)
    parms = .addParm(parms, k, as.numeric(v))
  }
  return(parms)
}

.addNumericParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.numeric(v)) stop(sprintf("%s must be of type numeric"), k)
    parms = .addParm(parms, k, v)
  }
  return(parms)
}

.addDoubleParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addFloatParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addLongParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addIntParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addNumericArrayParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.numeric(v)) stop(sprintf("%s must be of type numeric"), k)
    arrAsString = paste(v, collapse=",")
    parms = .addParm(parms, k, arrAsString)
  }
  return(parms)
}

.addDoubleArrayParm <- function(parms, k, v) {
  parms = .addNumericArrayParm(parms, k, v)
}

.addIntArrayParm <- function(parms, k, v) {
  parms = .addNumericArrayParm(parms, k, v)
}

# ---------------------------- Deep Learning - Neural Network ------------------------- #
h2o.deeplearning <- function(x, y, data, classification = TRUE, validation,
  # ----- AUTOGENERATED PARAMETERS BEGIN -----
  activation,
  hidden,
  epochs,
  train_samples_per_iteration,
  seed,
  adaptive_rate,
  rho,
  epsilon,
  rate,
  rate_annealing,
  rate_decay,
  momentum_start,
  momentum_ramp,
  momentum_stable,
  nesterov_accelerated_gradient,
  input_dropout_ratio,
  hidden_dropout_ratios,
  l1,
  l2,
  max_w2,
  initial_weight_distribution,
  initial_weight_scale,
  loss,
  score_interval,
  score_training_samples,
  score_validation_samples,
  score_duty_cycle,
  classification_stop,
  regression_stop,
  quiet_mode,
  max_confusion_matrix_size,
  max_hit_ratio_k,
  balance_classes,
  max_after_balance_size,
  score_validation_sampling,
  diagnostics,
  variable_importances,
  fast_mode,
  ignore_const_cols,
  force_load_balance,
  replicate_training_data,
  single_node_mode,
  shuffle_training_data,
  sparse,
  col_major
  # ----- AUTOGENERATED PARAMETERS END -----
)
{
  colargs <- .verify_dataxy(data, x, y)
  parms = list()

  parms$source = data@key
  parms$response = colargs$y
  parms$ignored_cols = colargs$x_ignore

  if (! missing(classification)) {
    if (! is.logical(classification)) stop('classification must be true or false')
    parms$classification = as.numeric(classification)
  }

  if (missing(validation)) validation = data
  parms$validation = validation@key

  # ----- AUTOGENERATED PARAMETERS BEGIN -----
  parms = .addStringParm(parms, k="activation", v=activation)
  parms = .addIntArrayParm(parms, k="hidden", v=hidden)
  parms = .addDoubleParm(parms, k="epochs", v=epochs)
  parms = .addLongParm(parms, k="train_samples_per_iteration", v=train_samples_per_iteration)
  parms = .addLongParm(parms, k="seed", v=seed)
  parms = .addBooleanParm(parms, k="adaptive_rate", v=adaptive_rate)
  parms = .addDoubleParm(parms, k="rho", v=rho)
  parms = .addDoubleParm(parms, k="epsilon", v=epsilon)
  parms = .addDoubleParm(parms, k="rate", v=rate)
  parms = .addDoubleParm(parms, k="rate_annealing", v=rate_annealing)
  parms = .addDoubleParm(parms, k="rate_decay", v=rate_decay)
  parms = .addDoubleParm(parms, k="momentum_start", v=momentum_start)
  parms = .addDoubleParm(parms, k="momentum_ramp", v=momentum_ramp)
  parms = .addDoubleParm(parms, k="momentum_stable", v=momentum_stable)
  parms = .addBooleanParm(parms, k="nesterov_accelerated_gradient", v=nesterov_accelerated_gradient)
  parms = .addDoubleParm(parms, k="input_dropout_ratio", v=input_dropout_ratio)
  parms = .addDoubleArrayParm(parms, k="hidden_dropout_ratios", v=hidden_dropout_ratios)
  parms = .addDoubleParm(parms, k="l1", v=l1)
  parms = .addDoubleParm(parms, k="l2", v=l2)
  parms = .addFloatParm(parms, k="max_w2", v=max_w2)
  parms = .addStringParm(parms, k="initial_weight_distribution", v=initial_weight_distribution)
  parms = .addDoubleParm(parms, k="initial_weight_scale", v=initial_weight_scale)
  parms = .addStringParm(parms, k="loss", v=loss)
  parms = .addDoubleParm(parms, k="score_interval", v=score_interval)
  parms = .addLongParm(parms, k="score_training_samples", v=score_training_samples)
  parms = .addLongParm(parms, k="score_validation_samples", v=score_validation_samples)
  parms = .addDoubleParm(parms, k="score_duty_cycle", v=score_duty_cycle)
  parms = .addDoubleParm(parms, k="classification_stop", v=classification_stop)
  parms = .addDoubleParm(parms, k="regression_stop", v=regression_stop)
  parms = .addBooleanParm(parms, k="quiet_mode", v=quiet_mode)
  parms = .addIntParm(parms, k="max_confusion_matrix_size", v=max_confusion_matrix_size)
  parms = .addIntParm(parms, k="max_hit_ratio_k", v=max_hit_ratio_k)
  parms = .addBooleanParm(parms, k="balance_classes", v=balance_classes)
  parms = .addFloatParm(parms, k="max_after_balance_size", v=max_after_balance_size)
  parms = .addStringParm(parms, k="score_validation_sampling", v=score_validation_sampling)
  parms = .addBooleanParm(parms, k="diagnostics", v=diagnostics)
  parms = .addBooleanParm(parms, k="variable_importances", v=variable_importances)
  parms = .addBooleanParm(parms, k="fast_mode", v=fast_mode)
  parms = .addBooleanParm(parms, k="ignore_const_cols", v=ignore_const_cols)
  parms = .addBooleanParm(parms, k="force_load_balance", v=force_load_balance)
  parms = .addBooleanParm(parms, k="replicate_training_data", v=replicate_training_data)
  parms = .addBooleanParm(parms, k="single_node_mode", v=single_node_mode)
  parms = .addBooleanParm(parms, k="shuffle_training_data", v=shuffle_training_data)
  parms = .addBooleanParm(parms, k="sparse", v=sparse)
  parms = .addBooleanParm(parms, k="col_major", v=col_major)
  # ----- AUTOGENERATED PARAMETERS END -----

  res = .h2o.__remoteSendWithParms(data@h2o, .h2o.__PAGE_DeepLearning, parms)

  noGrid = T
  if (noGrid) {
    .h2o.__waitOnJob(data@h2o, res$job_key)
    res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_DeepLearningModelView, '_modelKey'=res$destination_key)
    result = .h2o.__getDeepLearningResults(res2$deeplearning_model)
    new("H2ODeepLearningModel", key=res$destination_key, data=data, model=result, valid=validation)
  } else {
    # .h2o.gridsearch.internal("DeepLearning", data, res, validation, params)
    .h2o.gridsearch.internal("DeepLearning", data, res, validation)
  }
}

.h2o.__getDeepLearningSummary <- function(res) {
  mySum = list()
  resP = res$parameters

  mySum$model_key = resP$destination_key
  mySum$activation = resP$activation
  mySum$hidden = resP$hidden
  mySum$rate = resP$rate
  mySum$rate_annealing = resP$rate_annealing
  mySum$momentum_start = resP$momentum_start
  mySum$momentum_ramp = resP$momentum_ramp
  mySum$momentum_stable = resP$momentum_stable
  mySum$l1_reg = resP$l1
  mySum$l2_reg = resP$l2
  mySum$epochs = resP$epochs

  # temp = matrix(unlist(res$confusion_matrix), nrow = length(res$confusion_matrix))
  # mySum$prediction_error = 1-sum(diag(temp))/sum(temp)
  return(mySum)
}

.h2o.__getDeepLearningResults <- function(res, params = list()) {
  result = list()
#   model_params = res$model_info$parameters
#   params$activation = model_params$activation
#   params$rate = model_params$rate
#   params$annealing_rate = model_params$rate_annealing
#   params$l1_reg = model_params$l1
#   params$l2_reg = model_params$l2
#   params$mom_start = model_params$momentum_start
#   params$mom_ramp = model_params$momentum_ramp
#   params$mom_stable = model_params$momentum_stable
#   params$epochs = model_params$epochs

  # result$params = params
  model_params = res$model_info$parameters
  model_params$Request2 = NULL; model_params$response_info = NULL
  model_params$'source' = NULL; model_params$validation = NULL
  result$params = unlist(model_params, recursive = FALSE)
  result$params = lapply(result$params, function(x) { if(is.character(x)) { switch(x, true = TRUE, false = FALSE, "Inf" = Inf, "-Inf" = -Inf, x) }
                                                      else return(x) })
  errs = tail(res$errors, 1)[[1]]
  confusion = errs$valid_confusion_matrix

  # BUG: Why is the confusion matrix returning an extra row and column with all zeroes?
  if(!is.null(confusion$cm)) {
    cm = confusion$cm[-length(confusion$cm)]
    cm = lapply(cm, function(x) { x[-length(x)] })
    # result$confusion = .build_cm(cm, confusion$actual_domain, confusion$predicted_domain)
    result$confusion = .build_cm(cm, confusion$domain) 
  }
  result$train_class_error = errs$train_err
  result$train_sqr_error = errs$train_mse
  result$valid_class_error = errs$valid_err
  result$valid_sqr_error = errs$valid_mse

  if(!is.null(errs$validAUC)) {
      tmp <- .h2o.__getPerfResults(errs$validAUC)
      tmp$confusion <- NULL 
      result <- c(result, tmp) 
    }

  return(result)
}

# -------------------------------- Naive Bayes ----------------------------- #
h2o.naiveBayes <- function(x, y, data, laplace = 0, dropNACols = FALSE) {
  args <- .verify_dataxy(data, x, y)
  if(!is.numeric(laplace)) stop("laplace must be numeric")
  if(laplace < 0) stop("laplace must be a non-negative number")
  
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_BAYES, source = data@key, response = args$y, ignored_cols = args$x_ignore, laplace = laplace, drop_na_cols = as.numeric(dropNACols))
  .h2o.__waitOnJob(data@h2o, res$job_key)
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_NBModelView, '_modelKey' = res$destination_key)
  result = .h2o.__getNBResults(res2$nb_model)
  new("H2ONBModel", key = res$destination_key, data = data, model = result)
}

.h2o.__getNBResults <- function(res) {
  result = list()
  result$laplace = res$laplace
  result$levels = tail(res$'_domains',1)[[1]]
  result$apriori_prob = as.table(as.numeric(res$pprior))
  result$apriori = as.table(as.numeric(res$rescnt))
  dimnames(result$apriori) = dimnames(result$apriori_prob) = list(Y = result$levels)
  
  pred_names = res$'_names'[-length(res$'_names')]
  pred_domains = res$'_domains'[-length(res$'_domains')]
  result$tables = mapply(function(dat, nam, doms) {
                            if(is.null(doms))
                              doms = c("Mean", "StdDev")
                           temp = t(matrix(unlist(dat), nrow = length(doms)))
                            myList = list(result$levels, doms); names(myList) = c("Y", nam)
                            dimnames(temp) = myList
                            return(as.table(temp)) }, 
                         res$pcond, pred_names, pred_domains, SIMPLIFY = FALSE)
  names(result$tables) = pred_names
  return(result)
}

# ----------------------- Principal Components Analysis ----------------------------- #
h2o.prcomp <- function(data, tol=0, cols = "", standardize=TRUE, retx=FALSE) {
  args <- .verify_datacols(data, cols)
  if(!is.numeric(tol)) stop('tol must be numeric')
  if(!is.logical(standardize)) stop('standardize must be TRUE or FALSE')
  if(!is.logical(retx)) stop('retx must be TRUE or FALSE')

  destKey = .h2o.__uniqID("PCAModel")
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PCA, source=data@key, destination_key=destKey, ignored_cols = args$cols_ignore, tolerance=tol, standardize=as.numeric(standardize))
  .h2o.__waitOnJob(data@h2o, res$job_key)
  # while(!.h2o.__isDone(data@h2o, "PCA", res)) { Sys.sleep(1) }
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PCAModelView, '_modelKey'=destKey)
  res2 = res2$pca_model

  result = list()
  result$num_pc = res2$num_pc
  result$standardized = standardize
  result$sdev = res2$sdev
  nfeat = length(res2$eigVec[[1]])
  temp = t(matrix(unlist(res2$eigVec), nrow = nfeat))
  rownames(temp) = res2$namesExp #'_names'
  colnames(temp) = paste("PC", seq(1, ncol(temp)), sep="")
  result$rotation = temp

  if(retx) result$x = h2o.predict(new("H2OPCAModel", key=destKey, data=data, model=result))
  new("H2OPCAModel", key=destKey, data=data, model=result)
}

h2o.pcr <- function(x, y, data, ncomp, family, nfolds = 10, alpha = 0.5, lambda = 1.0e-5, epsilon = 1.0e-5, tweedie.p = ifelse(family=="tweedie", 0, as.numeric(NA))) {
  args <- .verify_dataxy(data, x, y)

  if( !is.numeric(nfolds) ) stop('nfolds must be numeric')
  if( nfolds < 0 ) stop('nfolds must be >= 0')
  if( !is.numeric(alpha) ) stop('alpha must be numeric')
  if( alpha < 0 ) stop('alpha must be >= 0')
  if( !is.numeric(lambda) ) stop('lambda must be numeric')
  if( lambda < 0 ) stop('lambda must be >= 0')

  cc = colnames(data)
  y <- args$y
  if( ncomp < 1 || ncomp > length(cc) ) stop("Number of components must be between 1 and ", ncol(data))

  x_ignore <- args$x_ignore
  x_ignore <- ifelse( x_ignore=='', y, c(x_ignore,y) )
  myModel <- .h2o.prcomp.internal(data=data, x_ignore=x_ignore, dest="", max_pc=ncomp, tol=0, standardize=TRUE)
  myScore <- h2o.predict(myModel)

  myScore[,ncomp+1] = data[,args$y_i]    # Bind response to frame of principal components
  myGLMData = new("H2OParsedData", h2o=data@h2o, key=myScore@key)
  h2o.glm.FV(x = 1:ncomp,
             y = ncomp+1,
             data = myGLMData,
             family = family,
             nfolds = nfolds,
             alpha = alpha,
             lambda = lambda,
             epsilon = epsilon,
             standardize = FALSE,
             tweedie.p = tweedie.p)
}

.h2o.prcomp.internal <- function(data, x_ignore, dest, max_pc=10000, tol=0, standardize=TRUE) {
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PCA, source=data@key, ignored_cols_by_name=x_ignore, destination_key=dest, max_pc=max_pc, tolerance=tol, standardize=as.numeric(standardize))
  .h2o.__waitOnJob(data@h2o, res$job_key)
  # while(!.h2o.__isDone(data@h2o, "PCA", res)) { Sys.sleep(1) }
  destKey = res$destination_key
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PCAModelView, '_modelKey'=destKey)
  res2 = res2$pca_model

  result = list()
  result$params$x = res2$'_names'
  result$num_pc = res2$num_pc
  result$standardized = standardize
  result$sdev = res2$sdev
  nfeat = length(res2$eigVec[[1]])
  temp = t(matrix(unlist(res2$eigVec), nrow = nfeat))
  rownames(temp) = res2$'namesExp'
  colnames(temp) = paste("PC", seq(1, ncol(temp)), sep="")
  result$rotation = temp
  new("H2OPCAModel", key=destKey, data=data, model=result)
}

# ----------------------------------- Random Forest --------------------------------- #
h2o.randomForest <- function(x, y, data, classification = TRUE, ntree = 50, depth = 20, sample.rate = 2/3,
    classwt = NULL, nbins = 100, seed = -1, importance = FALSE, validation, nodesize = 1,
    balance.classes = FALSE, max.after.balance.size = 5, use_non_local = TRUE, version = 2) {
  if(version == 1) {
    if(!missing(validation)) stop("validation not supported under ValueArray")
    if(nodesize != 1) stop("Random forest under ValueArray only runs on a single node")
    if(importance) stop("variable importance not supported under ValueArray")
    if(!classification) stop("regression not supported under ValueArray")
    if(balance.classes) stop("balance.classes not supported under ValueArray")
    h2o.randomForest.VA(x, y, data, ntree, depth, sample.rate, classwt, nbins, seed, use_non_local)
  } else if(version == 2) {
    if(!is.null(classwt)) stop("classwt not supported under FluidVecs - use balance_classes=TRUE instead.")
    h2o.randomForest.FV(x, y, data, classification, ntree, depth, sample.rate, nbins, seed, importance, validation, nodesize, balance.classes, max.after.balance.size)
  } else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

# -------------------------- ValueArray -------------------------- #
h2o.randomForest.VA <- function(x, y, data, ntree=50, depth=20, sample.rate=2/3, classwt=NULL, nbins=100, seed=-1, use_non_local=TRUE) {
  if(class(data) != "H2OParsedDataVA")
    stop("data must be of class H2OParsedDataVA. Please import data via h2o.importFile.VA or h2o.importFolder.VA")

  args <- .verify_dataxy(data, x, y)
  if(!is.numeric(ntree)) stop("ntree must be numeric")
  if(any(ntree <= 0)) stop("ntree must be > 0")
  if(!is.numeric(depth)) stop("depth must be numeric")
  if(any(depth < 0)) stop("depth must be >= 0")
  if(!is.numeric(sample.rate)) stop("sample.rate must be numeric")
  if(any(sample.rate < 0 | sample.rate > 1)) stop("sample.rate must be in [0,1]")
  if(!is.numeric(nbins)) stop('nbins must be a number')
  if(any(nbins < 1)) stop('nbins must be an integer >= 1')
  if(!is.numeric(seed)) stop("seed must be an integer >= 0")
  if(!is.logical(use_non_local)) stop("use_non_local must be logical indicating whether to use non-local data")

  if(!missing(ntree) && length(ntree) > 1 || !missing(depth) && length(depth) > 1 || !missing(sample.rate) && length(sample.rate) > 1 || !missing(nbins) && length(nbins) > 1)
    stop("Random forest grid search not supported under ValueArray")

  if(!is.numeric(classwt) && !is.null(classwt)) stop("classwt must be numeric")
  if(!is.null(classwt)) {
    y_col = data[,args$y_i]
    if(!is.factor(y_col)) stop("Cannot specify classwt: response column is not a factor!")

    nc <- names(classwt)
    if(is.null(nc) || any(nchar(nc) == 0)) stop("classwt must specify level names")

    lv <- levels(y_col)
    if(any(!(nc %in% lv)))
      stop(paste(paste(nc[!(nc %in% lv)], collapse=","), 'is not a valid level name'))
    classwt <- paste(nc, classwt, sep="=", collapse=",")
  }

  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_RF, data_key=data@key, response_variable=args$y, ignore=args$x_ignore, ntree=ntree, depth=depth, sample=round(100*sample.rate), class_weights=classwt, seed=seed, use_non_local_data=as.numeric(use_non_local))
  params = list(x=args$x, y=args$y, ntree=ntree, depth=depth, sample.rate=sample.rate)
  if(!is.null(classwt)) params$classwt = classwt
  .h2o.__waitOnJob(data@h2o, res$response$redirect_request_args$job)
  # while(!.h2o.__isDone(data@h2o, "RF1", res)) { Sys.sleep(1) }

  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_RFVIEW, model_key=res$destination_key, data_key=data@key, response_variable=args$y, out_of_bag_error_estimate=1)
  modelOrig = .h2o.__getRFResults(res2, params)
  new("H2ORFModelVA", key=res$destination_key, data=data, model=modelOrig)
}

.h2o.__getRFResults <- function(model, params) {
  result = list()
  result$params = params
  result$ntree = model$ntree
  result$classification_error = model$confusion_matrix$classification_error
  result$confusion = .build_cm(model$confusion_matrix$scores, model$confusion_matrix$header)
  result$depth_sum = unlist(model$trees$depth)
  result$leaves_sum = unlist(model$trees$leaves)
  result$tree_sum = matrix(c(model$trees$depth, model$trees$leaves), nrow=2, dimnames=list(c("Depth", "Leaves"), c("Min", "Mean", "Max")))
  return(result)
}

# -------------------------- FluidVecs -------------------------- #
h2o.randomForest.FV <- function(x, y, data, classification=TRUE, ntree=50, depth=20, sample.rate=2/3, nbins=100, seed=-1, importance=FALSE, validation, nodesize=1, balance.classes=FALSE, max.after.balance.size=5) {
  args <- .verify_dataxy(data, x, y)
  if(!is.logical(classification)) stop("classification must be logical (TRUE or FALSE)")
  if(!is.numeric(ntree)) stop('ntree must be a number')
  if( any(ntree < 1) ) stop('ntree must be >= 1')
  if(!is.numeric(depth)) stop('depth must be a number')
  if( any(depth < 1) ) stop('depth must be >= 1')
  if(!is.numeric(sample.rate)) stop('sample.rate must be a number')
  if( any(sample.rate < 0 || sample.rate > 1) ) stop('sample.rate must be between 0 and 1')
  if(!is.numeric(nbins)) stop('nbins must be a number')
  if( any(nbins < 1)) stop('nbins must be an integer >= 1')
  if(!is.numeric(seed)) stop("seed must be an integer >= 0")
  if(!is.logical(importance)) stop("importance must be logical (TRUE or FALSE)')")

  if(missing(validation)) validation = data
  # else if(class(validation) != "H2OParsedData") stop("validation must be an H2O dataset")
  else if(!class(validation) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("validation must be an H2O parsed dataset")
  if(!is.numeric(nodesize)) stop('nodesize must be a number')
  if( any(nodesize < 1) ) stop('nodesize must be >= 1')

  if(!is.logical(balance.classes)) stop('balance.classes must be logical (TRUE or FALSE)')
  if(!is.numeric(max.after.balance.size)) stop('max.after.balance.size must be a number')
  if( any(max.after.balance.size <= 0) ) stop('max.after.balance.size must be >= 0')
  if(balance.classes && !classification) stop('balance.classes can only be used for classification')

  # NB: externally, 1 based indexing; internally, 0 based
  cols <- paste(args$x_i - 1, collapse=',')
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_DRF, source=data@key, response=args$y, cols=cols, ntrees=ntree, max_depth=depth, min_rows=nodesize, sample_rate=sample.rate, nbins=nbins, seed=seed, importance=as.numeric(importance), classification=as.numeric(classification), validation=validation@key, balance_classes=as.numeric(balance.classes), max_after_balance_size=as.numeric(max.after.balance.size))
  params = list(x=args$x, y=args$y, ntree=ntree, depth=depth, sample.rate=sample.rate, nbins=nbins, importance=importance, balance.classes=balance.classes, max.after.balance.size=max.after.balance.size)

  if(length(ntree) == 1 && length(depth) == 1 && length(nodesize) == 1 && length(sample.rate) == 1 && length(nbins) == 1 && length(max.after.balance.size) == 1) {
    .h2o.__waitOnJob(data@h2o, res$job_key)
    # while(!.h2o.__isDone(data@h2o, "RF2", res)) { Sys.sleep(1) }
    res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_DRFModelView, '_modelKey'=res$destination_key)

    result = .h2o.__getDRFResults(res2$drf_model, params)
    new("H2ODRFModel", key=res$destination_key, data=data, model=result, valid=validation)
  } else {
    # .h2o.gridsearch.internal("RF", data, res$job_key, res$destination_key, validation, args$y_i)
    .h2o.gridsearch.internal("RF", data, res, validation, params)
  }
}

.h2o.__getDRFSummary <- function(res) {
  mySum = list()
  mySum$model_key = res$'_key'
  mySum$ntrees = res$N
  mySum$max_depth = res$max_depth
  mySum$min_rows = res$min_rows
  mySum$nbins = res$nbins
  mySum$balance_classes = res$balance_classes
  mySum$max_after_balance_size = res$max_after_balance_size

  # temp = matrix(unlist(res$cm), nrow = length(res$cm))
  # mySum$prediction_error = 1-sum(diag(temp))/sum(temp)
  mySum$prediction_error = tail(res$'cms', 1)[[1]]$'_predErr'
  return(mySum)
}

.h2o.__getDRFResults <- function(res, params) {
  result = list()
  params$ntree = res$N
  params$depth = res$max_depth
  params$nbins = res$nbins
  params$sample.rate = res$sample_rate
  params$classification = ifelse(res$parameters$classification == "true", TRUE, FALSE)
  params$balance.classes = res$balance_classes
  params$max.after.balance.size = res$max_after_balance_size

  result$params = params
  treeStats = unlist(res$treeStats)
  rf_matrix = rbind(treeStats[1:3], treeStats[4:6])
  colnames(rf_matrix) = c("Min.", "Max.", "Mean.")
  rownames(rf_matrix) = c("Depth", "Leaves")
  result$forest = rf_matrix
  result$mse = as.numeric(res$errs)

  if(params$classification) {
    if(!is.null(res$validAUC)) {
      tmp <- .h2o.__getPerfResults(res$validAUC)
      tmp$confusion <- NULL
      result <- c(result, tmp)
    }

    class_names = res$'cmDomain' # tail(res$'_domains', 1)[[1]]
    result$confusion = .build_cm(tail(res$'cms', 1)[[1]]$'_arr', class_names)  #res$'_domains'[[length(res$'_domains')]])
  }
  
  if(params$importance) {
    result$varimp = data.frame(rbind(res$varimp$varimp, res$varimp$varimpSD))
    result$varimp[3,] = sqrt(params$ntree)*result$varimp[1,]/result$varimp[2,]   # Compute z-scores
    colnames(result$varimp) = res$'_names'[-length(res$'_names')]    #res$varimp$variables
    rownames(result$varimp) = c(res$varimp$method, "Standard Deviation", "Z-Scores")
  }
  return(result)
}

# -------------------------- SpeeDRF -------------------------- #
h2o.SpeeDRF <- function(x, y, data, classification=TRUE, validation,
                        mtry=-1, 
                        ntree=50, 
                        depth=50, 
                        sample.rate=2/3,
                        oobee = TRUE,
                        importance = FALSE,
                        nbins=1024, 
                        seed=-1,
                        stat.type="ENTROPY",
                        classwt=NULL,
                        sampling_strategy = "RANDOM",
                        strata_samples=NULL) {
  args <- .verify_dataxy(data, x, y)
  if(!is.numeric(ntree)) stop('ntree must be a number')
  if( any(ntree < 1) ) stop('ntree must be >= 1')
  if(!is.numeric(depth)) stop('depth must be a number')
  if( any(depth < 1) ) stop('depth must be >= 1')
  if(!is.numeric(sample.rate)) stop('sample.rate must be a number')
  if( any(sample.rate < 0 || sample.rate > 1) ) stop('sample.rate must be between 0 and 1')
  if(!is.numeric(nbins)) stop('nbins must be a number')
  if( any(nbins < 1)) stop('nbins must be an integer >= 1')
  if(!is.numeric(seed)) stop("seed must be an integer")
  if(!(stat.type %in% c("ENTROPY", "GINI"))) stop(paste("stat.type must be either GINI or ENTROPY. Input was: ", stat.type, sep = ""))
  if(!(is.logical(oobee))) stop(paste("oobee must be logical (TRUE or FALSE). Input was: ", oobee, " and is of type ", mode(oobee), sep = ""))
  if(!(sampling_strategy %in% c("RANDOM", "STRATIFIED"))) stop(paste("sampling_strategy must be either RANDOM or STRATIFIED. Input was: ", sampling_strategy, sep = ""))
  
  if(!missing(ntree) && length(ntree) > 1 || !missing(depth) && length(depth) > 1 || !missing(sample.rate) && length(sample.rate) > 1 || !missing(nbins) && length(nbins) > 1) 
    stop("Random forest grid search not supported under SpeeDRF")

  if(!is.numeric(classwt) && !is.null(classwt)) stop("classwt must be numeric")
  if(!is.null(classwt)) {
    if(any(classwt < 0)) stop("Class weights must all be positive")
  }
  if(!is.null(strata_samples)) {
    if(any(strata_samples) < 0) stop("Strata samples must all be positive")
  }
  if(missing(validation)) validation <- data 
  else if(!class(validation) %in% c("H2OParsedData")) stop("validation must be an H2O parsed dataset!")

  # NB: externally, 1 based indexing; internally, 0 based
  cols <- paste(args$x_i - 1, collapse=',')
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_SpeeDRF, source=data@key, response=args$y, ignored_cols=args$x_ignore, num_trees=ntree, max_depth=depth, validation=validation@key, importance=as.numeric(importance),
                          sample=sample.rate, bin_limit=nbins, seed=seed, stat_type = stat.type, oobee=as.numeric(oobee), sampling_strategy=sampling_strategy, strata_samples=strata_samples, class_weights=classwt)

  params = list(x=args$x, y=args$y, ntree=ntree, depth=depth, sample.rate=sample.rate, bin_limit=nbins, stat.type = stat.type, classwt=classwt, sampling_strategy=sampling_strategy, seed=seed, oobee = oobee, importance = importance)

  if(length(ntree) == 1 && length(depth) == 1 && length(sample.rate) == 1 && length(nbins) == 1) { 
    .h2o.__waitOnJob(data@h2o, res$job_key)
    res2 <- .h2o.__remoteSend(data@h2o, .h2o.__PAGE_SpeeDRFModelView, '_modelKey'=res$destination_key)

    result <- .h2o.__getSpeeDRFResults(res2$speedrf_model, params)
    new("H2OSpeeDRFModel", key=res$destination_key, data=data, model=result, valid=validation)
  } else {
    .h2o.gridsearch.internal("SpeeDRF", data, res, validation, params)
  }
}

.h2o.__getSpeeDRFSummary <- function(res) {
  mySum = list()
  mySum$model_key = res$'_key'
  mySum$ntrees = res$N
  mySum$max_depth = res$max_depth
  mySum$min_rows = res$min_rows
  mySum$nbins = res$bin_limit

  # temp = matrix(unlist(res$cm), nrow = length(res$cm))
  # mySum$prediction_error = 1-sum(diag(temp))/sum(temp)
  return(mySum)
}

.h2o.__getSpeeDRFResults <- function(res, params) {
  result = list()
  params$ntree = res$N
  params$depth = res$max_depth
  params$nbins = res$nbins
  params$classification = TRUE

  result$params = params
  #treeStats = unlist(res$treeStats)
  #rf_matrix = rbind(treeStats[1:3], treeStats[4:6])
  #colnames(rf_matrix) = c("Min.", "Max.", "Mean.")
  #rownames(rf_matrix) = c("Depth", "Leaves")
  #result$forest = rf_matrix
  result$mse = as.numeric(res$errs)
  #result$mse <- ifelse(result$mse == -1, NA, result$mse)
  result$mse <- result$mse[length(result$mse)]

  if(params$classification) {
    #if(!is.null(res$validAUC)) {
    #  tmp <- .h2o.__getPerfResults(res$validAUC)
    #  tmp$confusion <- NULL
    #  result <- c(result, tmp)
    #}

    class_names = tail(res$'_domains', 1)[[1]]
    result$confusion = .build_cm(tail(res$cms, 1)[[1]]$'_arr', class_names)
  }

  if(params$importance) {
    result$varimp = data.frame(rbind(res$varimp$varimp, res$varimp$varimpSD))
    result$varimp[3,] = sqrt(params$ntree)*result$varimp[1,]/result$varimp[2,]   # Compute z-scores
    colnames(result$varimp) = res$'_names'[-length(res$'_names')]    #res$varimp$variables
    rownames(result$varimp) = c(res$varimp$method, "Standard Deviation", "Z-Scores")
  }

  return(result)
}
# ----------------------------------------------------------------------------------- #

# ------------------------------- Prediction ---------------------------------------- #
h2o.predict <- function(object, newdata) {
  if( missing(object) ) stop('Must specify object')
  if(!inherits(object, "H2OModel") && !inherits(object, "H2OModelVA")) stop("object must be an H2O model")
  if( missing(newdata) ) newdata <- object@data
  if(!class(newdata) %in% c('H2OParsedData', 'H2OParsedDataVA')) stop('newdata must be a H2O dataset')
  if(inherits(object, "H2OModelVA") && class(newdata) != "H2OParsedDataVA")
    stop("Prediction requires newdata to be of class H2OParsedDataVA")

  if(class(object) %in% c("H2OGLMModelVA", "H2ORFModelVA")) {
    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_PREDICT, model_key=object@key, data_key=newdata@key)
    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_INSPECT, key=res$response$redirect_request_args$key)
    new("H2OParsedDataVA", h2o=object@data@h2o, key=res$key)
  } else if(class(object) == "H2OKMeansModelVA") {
    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_KMAPPLY, model_key=object@key, data_key=newdata@key)
    .h2o.__waitOnJob(object@data@h2o, res$response$redirect_request_args$job)
    res2 = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_INSPECT, key=res$response$redirect_request_args$destination_key)
    new("H2OParsedDataVA", h2o=object@data@h2o, key=res2$key)
  } else if(class(object) %in% c("H2OGBMModel", "H2OKMeansModel", "H2ODRFModel", "H2OGLMModel", "H2ONBModel", "H2ODeepLearningModel", "H2OSpeeDRFModel")) {
    # Set randomized prediction key
    key_prefix = switch(class(object), "H2OGBMModel" = "GBMPredict", "H2OKMeansModel" = "KMeansPredict",
                                       "H2ODRFModel" = "DRFPredict", "H2OGLMModel" = "GLM2Predict", "H2ONBModel" = "NBPredict",
                                       "H2ODeepLearningModel" = "DeepLearningPredict", "H2OSpeeDRFModel" = "SpeeDRFPredict")
    rand_pred_key = .h2o.__uniqID(key_prefix)
    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_PREDICT2, model=object@key, data=newdata@key, prediction=rand_pred_key)
    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_INSPECT2, src_key=rand_pred_key)
    new("H2OParsedData", h2o=object@data@h2o, key=rand_pred_key)
  } else if(class(object) == "H2OPCAModel") {
    # Set randomized prediction key
    rand_pred_key = .h2o.__uniqID("PCAPredict")
    numMatch = colnames(newdata) %in% object@model$params$x
    numPC = min(length(numMatch[numMatch == TRUE]), object@model$num_pc)
    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_PCASCORE, source=newdata@key, model=object@key, destination_key=rand_pred_key, num_pc=numPC)
    .h2o.__waitOnJob(object@data@h2o, res$job_key)
    new("H2OParsedData", h2o=object@data@h2o, key=rand_pred_key)
  } else
    stop(paste("Prediction has not yet been implemented for", class(object)))
}

h2o.confusionMatrix <- function(data, reference) {
  if(!class(data) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("data must be an H2O parsed dataset")
  if(!class(reference) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("reference must be an H2O parsed dataset")
  if(ncol(data) != 1) stop("Must specify exactly one column for data")
  if(ncol(reference) != 1) stop("Must specify exactly one column for reference")

  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_CONFUSION, actual = reference@key, vactual = 0, predict = data@key, vpredict = 0)
  cm = lapply(res$cm[-length(res$cm)], function(x) { x[-length(x)] })
  # .build_cm(cm, res$actual_domain, res$predicted_domain, transpose = TRUE)
  .build_cm(cm, res$domain, transpose = TRUE)
}

h2o.hitRatio <- function(prediction, reference, k = 10, seed = 0) {
  if(!class(prediction) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("prediction must be an H2O parsed dataset")
  if(!class(reference) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("reference must be an H2O parsed dataset")
  if(ncol(reference) != 1) stop("Must specify exactly one column for reference")
  if(!is.numeric(k) || k < 1) stop("k must be an integer greater than 0")
  if(!is.numeric(seed)) stop("seed must be numeric")

  res = .h2o.__remoteSend(prediction@h2o, .h2o.__PAGE_HITRATIO, actual = reference@key, vactual = 0, predict = prediction@key, max_k = k, seed = seed)
  temp = res$hit_ratios; names(temp) = make.names(res$actual_domain)
  return(temp)
}

h2o.gapStatistic <- function(data, cols = "", K.max = 10, B = 100, boot_frac = 0.33, seed = 0) {
  args <- .verify_datacols(data, cols)
  if(!is.numeric(B) || B < 1) stop("B must be an integer greater than 0")
  if(!is.numeric(K.max) || K.max < 2) stop("K.max must be an integer greater than 1")
  if(!is.numeric(boot_frac) || boot_frac < 0 || boot_frac > 1) stop("boot_frac must be a number between 0 and 1")
  if(!is.numeric(seed)) stop("seed must be numeric")
  
  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GAPSTAT, source = data@key, b_max = B, k_max = K.max, bootstrap_fraction = boot_frac, seed = seed)
  .h2o.__waitOnJob(data@h2o, res$job_key)
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GAPSTATVIEW, '_modelKey' = res$destination_key)
  
  result = list()
  result$log_within_ss = res2$gap_model$wks
  result$boot_within_ss = res2$gap_model$wkbs
  result$se_boot_within_ss = res2$gap_model$sk
  result$gap_stats = res2$gap_model$gap_stats
  result$k_opt = res2$gap_model$k_best
  return(result)
}

h2o.performance <- function(data, reference, measure = "accuracy", thresholds) {
  if(!class(data) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("data must be an H2O parsed dataset")
  if(!class(reference) %in% c("H2OParsedData", "H2OParsedDataVA")) stop("reference must be an H2O parsed dataset")
  if(ncol(data) != 1) stop("Must specify exactly one column for data")
  if(ncol(reference) != 1) stop("Must specify exactly one column for reference")
  if(!measure %in% c("F1", "accuracy", "precision", "recall", "specificity", "max_per_class_error"))
    stop("measure must be one of [F1, accuracy, precision, recall, specificity, max_per_class_error]")
  if(!missing(thresholds) && !is.numeric(thresholds)) stop("thresholds must be a numeric vector")

  criterion = switch(measure, F1 = "maximum_F1", accuracy = "maximum_Accuracy", precision = "maximum_Precision",
                     recall = "maximum_Recall", specificity = "maximum_Specificity", max_per_class_error = "minimizing_max_per_class_Error")
  if(missing(thresholds))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_AUC, actual = reference@key, vactual = 0, predict = data@key, vpredict = 0, threshold_criterion = criterion)
  else
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_AUC, actual = reference@key, vactual = 0, predict = data@key, vpredict = 0, thresholds = .seq_to_string(thresholds), threshold_criterion = criterion)

  meas = as.numeric(res[[measure]])
  result = .h2o.__getPerfResults(res, criterion)
  roc = .get_roc(res$confusion_matrices)
  new("H2OPerfModel", cutoffs = res$thresholds, measure = meas, perf = measure, model = result, roc = roc)
}

.h2o.__getPerfResults <- function(res, criterion) {
  if(missing(criterion)) criterion = res$threshold_criterion
  criterion = gsub("_", " ", res$threshold_criterion)    # Note: For some reason, underscores turned into spaces in JSON threshold_criteria
  idx = which(criterion == res$threshold_criteria)

  result = list()
  result$auc = res$AUC
  result$gini = res$Gini
  result$best_cutoff = res$threshold_for_criteria[[idx]]
  result$F1 = res$F1_for_criteria[[idx]]
  result$accuracy = res$accuracy_for_criteria[[idx]]
  result$precision = res$precision_for_criteria[[idx]]
  result$recall = res$recall_for_criteria[[idx]]
  result$specificity = res$specificity_for_criteria[[idx]]
  result$max_per_class_err = res$max_per_class_error_for_criteria[[idx]]
  result = lapply(result, function(x) { if(x == "NaN") x = NaN; return(x) })   # HACK: NaN's are returned as strings, not numeric values

  # Note: Currently, Java assumes actual_domain = predicted_domain, but this may not always be true. Need to fix.
  result$confusion = .build_cm(res$confusion_matrix_for_criteria[[idx]], res$actual_domain)
  return(result)
}

plot.H2OPerfModel <- function(x, type = "cutoffs", ...) {
  if(!type %in% c("cutoffs", "roc")) stop("type must be either 'cutoffs' or 'roc'")
  if(type == "roc") {
    xaxis = "False Positive Rate"; yaxis = "True Positive Rate"
    plot(x@roc$FPR, x@roc$TPR, main = paste(yaxis, "vs", xaxis), xlab = xaxis, ylab = yaxis, ...)
    abline(0, 1, lty = 2)
  } else {
    xaxis = "Cutoff"; yaxis = .toupperFirst(x@perf)
    plot(x@cutoffs, x@measure, main = paste(yaxis, "vs.", xaxis), xlab = xaxis, ylab = yaxis, ...)
    abline(v = x@model$best_cutoff, lty = 2)
  }
}

# ------------------------------- Helper Functions ---------------------------------------- #
# Used to verify data, x, y and turn into the appropriate things
.verify_dataxy <- function(data, x, y) {
  if( missing(data) ) stop('Must specify data')
  if(!class(data) %in% c("H2OParsedData", "H2OParsedDataVA")) stop('data must be an H2O parsed dataset')

  if( missing(x) ) stop('Must specify x')
  if( missing(y) ) stop('Must specify y')
  if(!( class(x) %in% c('numeric', 'character', 'integer') )) stop('x must be column names or indices')
  if(!( class(y) %in% c('numeric', 'character', 'integer') )) stop('y must be a column name or index')

  cc <- colnames( data )
  if(is.character(x)) {
    if(any(!(x %in% cc))) stop(paste(paste(x[!(x %in% cc)], collapse=','), 'is not a valid column name'))
    x_i <- match(x, cc)
  } else {
    if(any( x < 1 | x > length(cc) )) stop(paste('Out of range explanatory variable', paste(x[x < 1 | x > length(cc)], collapse=',')))
    x_i <- x
    x <- cc[ x_i ]
  }

  if(is.character(y)){
    if(!( y %in% cc )) stop(paste(y, 'is not a column name'))
    y_i <- which(y == cc)
  } else {
    if( y < 1 || y > length(cc) ) stop(paste('Response variable index', y, 'is out of range'))
    y_i <- y
    y <- cc[ y ]
  }
  if( y %in% x ) stop(paste(y, 'is both an explanatory and dependent variable'))

  x_ignore <- setdiff(setdiff( cc, x ), y)
  if( length(x_ignore) == 0 ) x_ignore <- ''
  list(x=x, y=y, x_i=x_i, x_ignore=x_ignore, y_i=y_i)
}

.verify_datacols <- function(data, cols) {
  if( missing(data) ) stop('Must specify data')
  if(!class(data) %in% c("H2OParsedData", "H2OParsedDataVA")) stop('data must be an H2O parsed dataset')
  
  if( missing(cols) ) stop('Must specify cols')
  if(!( class(cols) %in% c('numeric', 'character', 'integer') )) stop('cols must be column names or indices')

  cc <- colnames(data)
  if(length(cols) == 1 && cols == '') cols = cc
  if(is.character(cols)) {
    # if(any(!(cols %in% cc))) stop(paste(paste(cols[!(cols %in% cc)], collapse=','), 'is not a valid column name'))
    if( any(!cols %in% cc) ) stop("Invalid column names: ", paste(cols[which(!cols %in% cc)], collapse=", "))
    cols_ind <- match(cols, cc)
  } else {
    if(any( cols < 1 | cols > length(cc))) stop(paste('Out of range explanatory variable', paste(cols[cols < 1 | cols > length(cc)], collapse=',')))
    cols_ind <- cols
    cols <- cc[cols_ind]
  }
  
  cols_ignore <- setdiff(cc, cols)
  if( length(cols_ignore) == 0 ) cols_ignore <- ''
  list(cols=cols, cols_ind=cols_ind, cols_ignore=cols_ignore)
}

# .h2o.gridsearch.internal <- function(algo, data, job_key, dest_key, validation = NULL, forGBMIsClassificationAndYesTheBloodyModelShouldReportIt=T) {
.h2o.gridsearch.internal <- function(algo, data, response, validation = NULL, params = list()) {
  if(!algo %in% c("GBM", "KM", "RF", "DeepLearning")) stop("General grid search not supported for ", algo)
  prog_view = switch(algo, GBM = .h2o.__PAGE_GBMProgress, KM = .h2o.__PAGE_KM2Progress, RF = .h2o.__PAGE_DRFProgress, DeepLearning = .h2o.__PAGE_DeepLearningProgress)

  job_key = response$job_key
  dest_key = response$destination_key
  .h2o.__waitOnJob(data@h2o, job_key)
  # while(!.h2o.__isDone(data@h2o, algo, response)) { Sys.sleep(1); prog = .h2o.__poll(data@h2o, job_key); setTxtProgressBar(pb, prog) }
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GRIDSEARCH, job_key=job_key, destination_key=dest_key)
  allModels = res2$jobs; allErrs = res2$prediction_error

  model_obj = switch(algo, GBM = "H2OGBMModel", KM = "H2OKMeansModel", RF = "H2ODRFModel", DeepLearning = "H2ODeepLearningModel")
  grid_obj = switch(algo, GBM = "H2OGBMGrid", KM = "H2OKMeansGrid", RF = "H2ODRFGrid", DeepLearning = "H2ODeepLearningGrid")
  model_view = switch(algo, GBM = .h2o.__PAGE_GBMModelView, KM = .h2o.__PAGE_KM2ModelView, RF = .h2o.__PAGE_DRFModelView, DeepLearning = .h2o.__PAGE_DeepLearningModelView)

  result = list(); myModelSum = list()
  for(i in 1:length(allModels)) {
    if(algo == "KM")
      resH = .h2o.__remoteSend(data@h2o, model_view, model=allModels[[i]]$destination_key)
    else
      resH = .h2o.__remoteSend(data@h2o, model_view, '_modelKey'=allModels[[i]]$destination_key)

    myModelSum[[i]] = switch(algo, GBM = .h2o.__getGBMSummary(resH[[3]], params), KM = .h2o.__getKM2Summary(resH[[3]]), RF = .h2o.__getDRFSummary(resH[[3]]), DeepLearning = .h2o.__getDeepLearningSummary(resH[[3]]))
    myModelSum[[i]]$prediction_error = allErrs[[i]]
    myModelSum[[i]]$run_time = allModels[[i]]$end_time - allModels[[i]]$start_time
    modelOrig = switch(algo, GBM = .h2o.__getGBMResults(resH[[3]], params), KM = .h2o.__getKM2Results(resH[[3]], data, params), RF = .h2o.__getDRFResults(resH[[3]], params), DeepLearning = .h2o.__getDeepLearningResults(resH[[3]], params))

    if(algo == "KM")
      result[[i]] = new(model_obj, key=allModels[[i]]$destination_key, data=data, model=modelOrig)
    else
      result[[i]] = new(model_obj, key=allModels[[i]]$destination_key, data=data, model=modelOrig, valid=validation)
  }
  new(grid_obj, key=dest_key, data=data, model=result, sumtable=myModelSum)
}

.build_cm <- function(cm, actual_names = NULL, predict_names = actual_names, transpose = TRUE) {
  #browser()
  categories = length(cm)
  cf_matrix = matrix(unlist(cm), nrow=categories)
  if(transpose) cf_matrix = t(cf_matrix)

  cf_total = apply(cf_matrix, 2, sum)
  # cf_error = c(apply(cf_matrix, 1, sum)/diag(cf_matrix)-1, 1-sum(diag(cf_matrix))/sum(cf_matrix))
  cf_error = c(1-diag(cf_matrix)/apply(cf_matrix,1,sum), 1-sum(diag(cf_matrix))/sum(cf_matrix))
  cf_matrix = rbind(cf_matrix, cf_total)
  cf_matrix = cbind(cf_matrix, round(cf_error, 3))

  if(!is.null(actual_names))
    dimnames(cf_matrix) = list(Actual = c(actual_names, "Totals"), Predicted = c(predict_names, "Error"))
  return(cf_matrix)
}

.get_roc <- function(cms) {
  tmp = sapply(cms, function(x) { c(TN = x[[1]][[1]], FP = x[[1]][[2]], FN = x[[2]][[1]], TP = x[[2]][[2]]) })
  tmp = data.frame(t(tmp))
  tmp$TPR = tmp$TP/(tmp$TP + tmp$FN)
  tmp$FPR = tmp$FP/(tmp$FP + tmp$TN)
  return(tmp)
}

.seq_to_string <- function(vec = as.numeric(NA)) {
  vec <- sort(vec)
  if(length(vec) > 2) {
    vec_diff = diff(vec)
    if(abs(max(vec_diff) - min(vec_diff)) < .Machine$double.eps^0.5)
      return(paste(min(vec), max(vec), vec_diff[1], sep = ":"))
  }
  return(paste(vec, collapse = ","))
}

.toupperFirst <- function(str) {
  paste(toupper(substring(str, 1, 1)), substring(str, 2), sep = "")
}
