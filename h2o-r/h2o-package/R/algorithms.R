h2o.kmeans <- function(data, centers, cols = '', key = "", iter.max = 10, normalize = FALSE, init = "none", seed = 0, dropNACols = FALSE) {
  args <- .verify_datacols(data, cols)

  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
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

  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_KMEANS2, source=data@key, destination_key=key, ignored_cols=args$cols_ignore, k=centers, max_iter=iter.max, normalize=as.numeric(normalize), initialization=myInit, seed=seed, drop_na_cols=as.numeric(dropNACols))
  params = list(cols=args$cols, centers=centers, iter.max=iter.max, normalize=normalize, init=myInit, seed=seed)

  if(.is_singlerun("KM", params)) {
    .h2o.__waitOnJob(data@h2o, res$job_key)
    # while(!.h2o.__isDone(data@h2o, "KM", res)) { Sys.sleep(1) }
    res2 = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_KM2ModelView, '_modelKey'=res$destination_key)
    res2 = res2$model

    result = .h2o.__getKM2Results(res2, data, params)
    new("H2OKMeansModel", key=res2$'_key', data=data, model=result)
  } else {
    # .h2o.gridsearch.internal("KM", data, res$job_key, res$destination_key)
    .h2o.gridsearch.internal("KM", data, res, params=params)
  }
}