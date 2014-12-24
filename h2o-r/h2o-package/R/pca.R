# ----------------------- Principal Components Analysis ----------------------------- #
h2o.prcomp <- function(data, tol=0, cols = "", max_pc = 5000, key = "", standardize=TRUE, retx=FALSE) {
  args <- .verify_datacols(data, cols)

  if(!is.numeric(tol)) stop("`tol` must be numeric")
  if(!is.numeric(max_pc)) stop("`max_pc` must be numeric")
  if(!is.character(key) || length(key) != 1L || is.na(key)) stop("`key` must be a character string")
  if(nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
    stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(standardize) || length(standardize) != 1L || is.na(standardize)) stop("`standardize` must be TRUE or FALSE")
  if(!is.logical(retx) || length(retx) != 1L || is.na(retx)) stop("`retx` must be TRUE or FALSE")

  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PCA, source=data@key, destination_key=key, ignored_cols = args$cols_ignore, tolerance=tol, standardize=as.numeric(standardize))
  .h2o.__waitOnJob(data@h2o, res$job_key)
  destKey = res$destination_key
  # while(!.h2o.__isDone(data@h2o, "PCA", res)) { Sys.sleep(1) }
  res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PCAModelView, '_modelKey'=destKey)
  res2 = res2$pca_model

  result = list()
  result$params$names = res2$'_names'
  result$params$x = res2$namesExp
  result$num_pc = res2$num_pc
  result$standardized = standardize
  result$sdev = res2$sdev
  nfeat = length(res2$eigVec[[1L]])
  if(max_pc > nfeat) max_pc = nfeat
  temp = t(matrix(unlist(res2$eigVec), nrow = nfeat))[,seq_len(max_pc)]
  temp = as.data.frame(temp)
  rownames(temp) = res2$namesExp #'_names'
  colnames(temp) = paste0("PC", 0L:(ncol(temp)-1L))
  result$rotation = temp

  if(retx) result$x = h2o.predict(new("H2OPCAModel", key=destKey, data=data, model=result), num_pc = max_pc)
  new("H2OPCAModel", key=destKey, data=data, model=result)
}


h2o.pcr <- function(x, y, data, key = "", ncomp, family, nfolds = 10, alpha = 0.5, lambda = 1.0e-5, epsilon = 1.0e-5, tweedie.p = ifelse(family=="tweedie", 0, as.numeric(NA))) {
  args <- .verify_dataxy(data, x, y)

  if(!is.character(key) || length(key) != 1L || is.na(key)) stop("`key` must be a character string")
  if(nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
    stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.numeric(nfolds)) stop('`nfolds` must be numeric')
  if(nfolds < 0L) stop('`nfolds` must be >= 0')
  if(!is.numeric(alpha)) stop('`alpha` must be numeric')
  if(alpha < 0) stop('`alpha` must be >= 0')
  if(!is.numeric(lambda)) stop('`lambda` must be numeric')
  if(lambda < 0) stop('`lambda` must be >= 0')

  cc = colnames(data)
  y <- args$y
  if(ncomp < 1L || ncomp > length(cc)) stop("Number of components must be between 1 and ", ncol(data))

  x_ignore <- args$x_ignore
  x_ignore <- ifelse( x_ignore=='', y, c(x_ignore,y) )
  myModel <- .h2o.prcomp.internal(data=data, x_ignore=x_ignore, dest="", max_pc=ncomp, tol=0, standardize=TRUE)
  myScore <- h2o.predict(myModel, num_pc = ncomp)

  myScore[,ncomp+1L] = data[,args$y_i]    # Bind response to frame of principal components
  myGLMData = .h2o.exec2(myScore@key, h2o = data@h2o, myScore@key)
  h2o.glm(x = seq_len(ncomp),
          y = ncomp+1L,
          data = myGLMData,
          key = key,
          family = family,
          nfolds = nfolds,
          alpha = alpha,
          lambda = lambda,
          epsilon = epsilon,
          standardize = FALSE,
          tweedie.p = tweedie.p)
}

.h2o.prcomp.internal <- function(data, x_ignore, dest, max_pc=5000, tol=0, standardize=TRUE) {
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
  nfeat = length(res2$eigVec[[1L]])
  temp = t(matrix(unlist(res2$eigVec), nrow = nfeat))
  rownames(temp) = res2$'namesExp'
  colnames(temp) = paste0("PC", seq_len(ncol(temp)))
  result$rotation = temp
  new("H2OPCAModel", key=destKey, data=data, model=result)
}

.get.pca.results <- function(data, json, destKey, params) {
  json$params <- params
  json$rotation <- t(matrix(unlist(json$eigVec), nrow = length(json$eigVec[[1L]])))
  rownames(json$rotation) <- json$'namesExp'
  colnames(json$rotation) <- paste0("PC", seq_len(ncol(json$rotation)))
  new("H2OPCAModel", key = destKey, data = data, model = json)
}
