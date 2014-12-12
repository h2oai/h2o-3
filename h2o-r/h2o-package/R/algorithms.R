.checkargs <- function(message, ...) {
  failed <- FALSE
  tryCatch(stopifnot(...), error = function(e) {failed <<- TRUE; print(message)})
  if (failed) error(message)
}

.convertFieldType <- function(json) {
   x <- json$actual_value
   switch(json$type,
   'Key' = as.character(x),
   'Frame' = as.character(x),
   'int' = as.integer(x),
   'boolean' = as.logical(x),
   'long' = as.numeric(x),
   'enum' = as.character(x),
   'float' = as.numeric(x),
   'double' = as.double(x)
   # 'double[]'
   # 'string[]'
   )
}


# ------------------------------- Helper Functions --------------------------- #
# Used to verify data, x, y and turn into the appropriate things
.verify_dataxy <- function(data, x, y) {
   .verify_dataxy_full(data, x, y, FALSE)
}
.verify_dataxy_full <- function(data, x, y, autoencoder) {
  if( missing(data) ) stop('Must specify data')
  if(class(data) != "h2o.frame") stop('data must be an H2O parsed dataset')

  if( missing(x) ) stop('Must specify x')
  if( missing(y) ) stop('Must specify y')
  if(!( class(x) %in% c('numeric', 'character', 'integer') ))
    stop('x must be column names or indices')
  if(!( class(y) %in% c('numeric', 'character', 'integer') ))
    stop('y must be a column name or index')

  cc <- colnames( data )
  if(is.character(x)) {
    if(any(!(x %in% cc))) stop(paste(paste(x[!(x %in% cc)], collapse=','),
      'is not a valid column name'))
    x_i <- match(x, cc)
  } else {
    if(any( x < 1 | x > length(cc) )) stop(paste('Out of range explanatory variable',
      paste(x[x < 1 | x > length(cc)], collapse=',')))
    x_i <- x
    x <- cc[ x_i ]
  }
  if(is.character(y)){
    if(!( y %in% cc )) stop(paste(y, 'is not a column name'))
    y_i <- which(y == cc)
  } else {
    if( y < 1 || y > length(cc) ) stop(paste('Response variable index', y,
      'is out of range'))
    y_i <- y
    y <- cc[ y ]
  }

  if (!missing(autoencoder) && !autoencoder) if( y %in% x ) {
    # stop(paste(y, 'is both an explanatory and dependent variable'))
    warning("Response variable in explanatory variables")
    x <- setdiff(x,y)
  }

  x_ignore <- setdiff(setdiff( cc, x ), y)
  if( length(x_ignore) == 0 ) x_ignore <- ''
#  if (is.character(x)) x <- .collapse(x)
#  if (is.character(x_ignore)) x_ignore <- .collapse(x_ignore)
  list(x=x, y=y, x_i=x_i, x_ignore=x_ignore, y_i=y_i)
}

.verify_datacols <- function(data, cols) {
  if( missing(data) ) stop('Must specify data')
  if(!(data %i% "h2o.frame")) stop('data must be an H2O parsed dataset')

  if( missing(cols) ) stop('Must specify cols')
  if(!( class(cols) %in% c('numeric', 'character', 'integer') )) stop('cols must
    be column names or indices')

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
  l <- list(cols=cols, cols_ind=cols_ind, cols_ignore=cols_ignore)
  return(l)
}

.h2o.singlerun.internal <- function(algo, data, response, nfolds = 0, validation = new("h2o.frame", key = as.character(NA)), params = list()) {
  if(!algo %in% c("GBM", "RF", "DeepLearning", "SpeeDRF")) stop("Unsupported algorithm ", algo)
  if(missing(validation)) validation = new("h2o.frame", key = as.character(NA))
  model_obj <- switch(algo, GBM = "H2OGBMModel", RF = "H2ODRFModel", DeepLearning = "H2ODeepLearningModel", SpeeDRF = "H2OSpeeDRFModel")
  model_view <- switch(algo, GBM = .h2o.__PAGE_GBMModelView, RF = .h2o.__PAGE_DRFModelView, DeepLearning = .h2o.__PAGE_DeepLearningModelView, SpeeDRF = .h2o.__PAGE_SpeeDRFModelView)
  results_fun <- switch(algo, GBM = .h2o.__getGBMResults, RF = .h2o.__getDRFResults, DeepLearning = .h2o.__getDeepLearningResults, SpeeDRF = .h2o.__getSpeeDRFResults)

  job_key <- response$job_key
  dest_key <- response$destination_key
  .h2o.__waitOnJob(data@h2o, job_key)
  # while(!.h2o.__isDone(data@h2o, algo, response)) { Sys.sleep(1) }
  res2 <- .h2o.__remoteSend(data@h2o, model_view, '_modelKey'=dest_key)
  modelOrig <- results_fun(res2[[3]], params)
  if (algo == "DeepLearning" && !is.null(modelOrig$validationKey)) validation@key = modelOrig$validationKey

  res_xval <- .h2o.crossvalidation(algo, data, res2[[3]], nfolds, params)
  new(model_obj, key=dest_key, data=data, model=modelOrig, valid=validation, xval=res_xval)
}

.h2o.gridsearch.internal <- function(algo, data, response, nfolds = 0, validation = new("h2o.frame", key = as.character(NA)), params = list()) {
  if(!algo %in% c("GBM", "KM", "RF", "DeepLearning", "SpeeDRF")) stop("General grid search not supported for ", algo)
  if(missing(validation)) validation <- new("h2o.frame", key = as.character(NA))
  prog_view <- switch(algo, GBM = .h2o.__PAGE_GBMProgress, KM = .h2o.__PAGE_KM2Progress, RF = .h2o.__PAGE_DRFProgress, DeepLearning = .h2o.__PAGE_DeepLearningProgress, SpeeDRF = .h2o.__PAGE_SpeeDRFProgress)

  job_key <- response$job_key
  dest_key <- response$destination_key
  .h2o.__waitOnJob(data@h2o, job_key)
  # while(!.h2o.__isDone(data@h2o, algo, response)) { Sys.sleep(1); prog = .h2o.__poll(data@h2o, job_key); setTxtProgressBar(pb, prog) }
  res2 <- .h2o.__remoteSend(data@h2o, .h2o.__PAGE_GRIDSEARCH, job_key=job_key, destination_key=dest_key)
  allModels <- res2$jobs; allErrs = res2$prediction_error

  model_obj <- switch(algo, GBM = "H2OGBMModel", KM = "H2OKMeansModel", RF = "H2ODRFModel", DeepLearning = "H2ODeepLearningModel", SpeeDRF = "H2OSpeeDRFModel")
  grid_obj <- switch(algo, GBM = "H2OGBMGrid", KM = "H2OKMeansGrid", RF = "H2ODRFGrid", DeepLearning = "H2ODeepLearningGrid", SpeeDRF = "H2OSpeeDRFGrid")
  model_view <- switch(algo, GBM = .h2o.__PAGE_GBMModelView, KM = .h2o.__PAGE_KM2ModelView, RF = .h2o.__PAGE_DRFModelView, DeepLearning = .h2o.__PAGE_DeepLearningModelView, SpeeDRF = .h2o.__PAGE_SpeeDRFModelView)
  results_fun <- switch(algo, GBM = .h2o.__getGBMResults, KM = .h2o.__getKM2Results, RF = .h2o.__getDRFResults, DeepLearning = .h2o.__getDeepLearningResults, SpeeDRF = .h2o.__getSpeeDRFResults)
  result <- list(); myModelSum = list()
  for(i in 1:length(allModels)) {
    if(algo == "KM")
      resH <- .h2o.__remoteSend(data@h2o, model_view, model=allModels[[i]]$destination_key)
    else
      resH <- .h2o.__remoteSend(data@h2o, model_view, '_modelKey'=allModels[[i]]$destination_key)

    myModelSum[[i]] <- switch(algo, GBM = .h2o.__getGBMSummary(resH[[3]], params), KM = .h2o.__getKM2Summary(resH[[3]]), RF = .h2o.__getDRFSummary(resH[[3]]), DeepLearning = .h2o.__getDeepLearningSummary(resH[[3]]), .h2o.__getSpeeDRFSummary(resH[[3]]))
    myModelSum[[i]]$prediction_error <- allErrs[[i]]
    myModelSum[[i]]$run_time <- allModels[[i]]$end_time - allModels[[i]]$start_time
    modelOrig <- results_fun(resH[[3]], params)

    if(algo == "KM")
      result[[i]] = new(model_obj, key=allModels[[i]]$destination_key, data=data, model=modelOrig)
    else {
      res_xval = .h2o.crossvalidation(algo, data, resH[[3]], nfolds, params)
      result[[i]] = new(model_obj, key=allModels[[i]]$destination_key, data=data, model=modelOrig, valid=validation, xval=res_xval)
    }
  }

  x <- pred_errs_orig <- unlist(lapply(seq_along(myModelSum),  function(x) myModelSum[[x]]$prediction_error))
  y <- pred_errs <- sort(pred_errs_orig)
  result <- result[order(match(x,y))]
  myModelSum <- myModelSum[order(match(x,y))]

  new(grid_obj, key=dest_key, data=data, model=result, sumtable=myModelSum)
}

.h2o.crossvalidation <- function(algo, data, resModel, nfolds = 0, params = list()) {
  if(!algo %in% c("GBM", "RF", "DeepLearning", "SpeeDRF")) stop("Cross-validation modeling not supported for ", algo)
  if(nfolds == 0) return(list())

  model_obj <- switch(algo, GBM = "H2OGBMModel", KM = "H2OKMeansModel", RF = "H2ODRFModel", DeepLearning = "H2ODeepLearningModel", SpeeDRF = "H2OSpeeDRFModel")
  model_view <- switch(algo, GBM = .h2o.__PAGE_GBMModelView, KM = .h2o.__PAGE_KM2ModelView, RF = .h2o.__PAGE_DRFModelView, DeepLearning = .h2o.__PAGE_DeepLearningModelView, SpeeDRF = .h2o.__PAGE_SpeeDRFModelView)
  results_fun <- switch(algo, GBM = .h2o.__getGBMResults, KM = .h2o.__getKM2Results, RF = .h2o.__getDRFResults, DeepLearning = .h2o.__getDeepLearningResults, SpeeDRF = .h2o.__getSpeeDRFResults)

  res_xval <- list()
  if(algo == "DeepLearning")
    xvalKey <- resModel$model_info$job$xval_models
  else
    xvalKey <- resModel$parameters$xval_models
  for(i in 1:nfolds) {
      resX <- .h2o.__remoteSend(data@h2o, model_view, '_modelKey'=xvalKey[i])
      modelXval <- results_fun(resX[[3]], params)
      res_xval[[i]] <- new(model_obj, key=xvalKey[i], data=data, model=modelXval, valid=new("h2o.frame", key=as.character(NA)), xval=list())
    }
  return(res_xval)
}

.is_singlerun <- function(algo, params = list()) {
  if(!algo %in% c("GBM", "KM", "RF", "SpeeDRF")) stop("Unrecognized algorithm: ", algo)
  if(algo == "GBM")
    my_params <- list(params$n.trees, params$interaction.depth, params$n.minobsinnode, params$shrinkage)
  else if(algo == "KM")
    my_params <- list(params$centers, params$iter.max)
  else if(algo == "RF")
    my_params <- list(params$ntree, params$depth, params$nodesize, params$sample.rate, params$nbins, params$max.after.balance.size)
  else if(algo == "SpeeDRF")
    my_params <- list(params$ntree, params$depth, params$sample.rate, params$nbins)

  isSingle <- all(sapply(my_params, function(x) { length(x) == 1 }))
  return(isSingle)
}

.build_cm <- function(cm, actual_names = NULL, predict_names = actual_names, transpose = TRUE) {
  #browser()
  categories <- length(cm)
  cf_matrix <- matrix(unlist(cm), nrow=categories)
  if(transpose) cf_matrix = t(cf_matrix)

  cf_total <- apply(cf_matrix, 2, sum)
  # cf_error <- c(apply(cf_matrix, 1, sum)/diag(cf_matrix)-1, 1-sum(diag(cf_matrix))/sum(cf_matrix))
  cf_error <- c(1-diag(cf_matrix)/apply(cf_matrix,1,sum), 1-sum(diag(cf_matrix))/sum(cf_matrix))
  cf_matrix <- rbind(cf_matrix, cf_total)
  cf_matrix <- cbind(cf_matrix, round(cf_error, 3))

  if(!is.null(actual_names))
    dimnames(cf_matrix) = list(Actual = c(actual_names, "Totals"), Predicted = c(predict_names, "Error"))
  return(cf_matrix)
}

.get_roc <- function(cms) {
  tmp <- sapply(cms, function(x) { c(TN = x[[1]][[1]], FP = x[[1]][[2]], FN = x[[2]][[1]], TP = x[[2]][[2]]) })
  tmp <- data.frame(t(tmp))
  tmp$TPR <- tmp$TP/(tmp$TP + tmp$FN)
  tmp$FPR <- tmp$FP/(tmp$FP + tmp$TN)
  return(tmp)
}

.seq_to_string <- function(vec = as.numeric(NA)) {
  vec <- sort(vec)
  if(length(vec) > 2) {
    vec_diff <- diff(vec)
    if(abs(max(vec_diff) - min(vec_diff)) < .Machine$double.eps^0.5)
      return(paste(min(vec), max(vec), vec_diff[1], sep = ":"))
  }
  return(paste(vec, collapse = ","))
}

.toupperFirst <- function(str) {
  paste(toupper(substring(str, 1, 1)), substring(str, 2), sep = "")
}



.parms <- function(client, algo, m, envir) {
  P <- .h2o.__remoteSend(client, method = "POST",  .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]]$parameters
  p_val <- list()     # list for all of the algo arguments
  m <- as.list(m)

  m <- lapply(m, function(i)  {
                                if( is.name(i) ) i <- get(deparse(i), envir)
                                if( is.call(i) ) i <- eval(i, envir)
                                if( is.integer(i) ) i <- as.numeric(i)
                                i
                                })

  #---------- Check user param types ----------#
  error <- lapply(P, function(i) {
    e <- ""
    if( i$required && !(i$name %in% names(m)) )
      e %p0% ("argument \"" %p0% ( i$name %p0% "\" is missing, with no default\n"))
    else if( i$name %in% names(m) ) {
      # changing Java types to R types
      tryCatch(p_type <- .type.map[[i$type]], error = function(e) stop("Cannot find type" %p% (i$type %p% "in .type.map.")))
      switch(p_type, #create two type parameters for arrays
        "sarray" = p_type[2] <- "character",
        "barray" = p_type[2] <- "logical",
        "narray" = p_type[2] <- "numeric")
      #browser()
      if( length(p_type) > 1 ) {
        p_type <- p_type[2]
        if( !(m[[i$name]] %i% p_type) )
          e %p0% ("array of" %p% i$name %p% ("must be of type" %p% (p_type %p0% (", but got" %p% (class(m[[i$name]]) %p0% ".\n")))))
          #         else p_val[[parm]] <- .collapse(m[[parm]])
        else m[[i$name]] <<- .collapse(m[[i$name]])
      } else if( !((m[[i$name]]) %i% p_type)  )
        e %p0% ("\"" %p0% i$name %p0% ("\" must be of type" %p% (p_type %p0% (", but got" %p% (class(m[[i$name]]) %p0% ".\n")))))
      else if( length(i$values) > 1)
        if( !(m[[i$name]] %in% i$values) ) {
          e %p0% ("\"" %p0% i$name %p0% ("\" must be in"))
          for(fact in i$values) e %p% ("\"" %p0% (fact %p0% "\","))
          e %p% ("but got" %p% m[[i$name]])
        }
      }
    e
  })


  if( !all(error == "") ) stop(error)

  #---------- Create param list to pass ----------#
  p_val <- lapply(m, function(i) {
    if( i %i% "h2o.frame" )
      return(i@key)
    i
  })
  #---------- Verify Params ----------#
  rj <- .h2o.__remoteSend(client, method = "POST", .h2o.__MODEL_BUILDERS(algo) %p0% "/parameters" , .params = p_val)

  if(length(rj$validation_messages) != 0)
    error <- lapply(rj$validation_messages, function(i) {
      e <- ""
      if( !(i$message_type %in% c("HIDE","INFO")) ) e %p0% i$message %p0% ".\n"
      e
    })
   if( !all(error == "") ) stop(error)

  #---------- Return Params ----------#
  #browser()  #uncomment to view values/types
  p_val
}

.run <- function(client, algo, m, envir) {
  p_val <- .parms(client, algo, m, envir)

  res <- .h2o.__remoteSend(client, method = "POST", .h2o.__MODEL_BUILDERS(algo), .params = p_val)

  job_key <- res$key$name
  dest_key <- res$jobs[[1]]$dest$name
  .h2o.__waitOnJob(client, job_key)
  # Grab model output and flatten one level
  res_model <- .h2o.__remoteSend(client, method = "POST", .h2o.__MODELS %p0% dest_key)

  res_model <- unlist(res_model, recursive = F)
  res_model <- res_model$models

  .newModel(algo, res_model, client)
}


.newModel <- function(algo, json, client) {
  do.call(.algo.map[[algo]], list(json, client))
}
