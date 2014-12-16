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
  P <- .h2o.__remoteSend(client, method = "GET",  .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]]$parameters
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

  job_key <- res$job[[1]]$key$name
  dest_key <- res$jobs[[1]]$dest$name
  .h2o.__waitOnJob(client, job_key)
  # Grab model output and flatten one level
  res_model <- .h2o.__remoteSend(client, method = "GET", .h2o.__MODELS %p0% "/" %p0% dest_key)

  res_model <- unlist(res_model, recursive = F)
  res_model <- res_model$models
  .newModel(algo, res_model, client)
}


.newModel <- function(algo, json, client) {
  do.call(.algo.map[[algo]], list(json, client))
}
