# ------------------------------- Helper Functions --------------------------- #
# Used to verify data, x, y and turn into the appropriate things
.verify_dataxy <- function(data, x, y) {
   .verify_dataxy_full(data, x, y, FALSE)
}
.verify_dataxy_full <- function(data, x, y, autoencoder) {
  if(!is(data,  "H2OFrame"))
    stop('`data` must be an H2OFrame object')
  if(!is.character(x) && !is.numeric(x))
    stop('`x` must be column names or indices')
  if(!is.character(y) && !is.numeric(y))
    stop('`y` must be a column name or index')

  cc <- colnames(data)

  if(is.character(x)) {
    if(!all(x %in% cc))
      stop("Invalid column names: ", paste(x[!(x %in% cc)], collapse=','))
    x_i <- match(x, cc)
  } else {
    if(any( x < 1L | x > length(cc)))
      stop('out of range explanatory variable ', paste(x[x < 1L | x > length(cc)], collapse=','))
    x_i <- x
    x <- cc[x_i]
  }

  if(is.character(y)){
    if(!(y %in% cc))
      stop(y, ' is not a column name')
    y_i <- which(y == cc)
  } else {
    if(y < 1L || y > length(cc))
      stop('response variable index ', y, ' is out of range')
    y_i <- y
    y <- cc[y]
  }

  if(!missing(autoencoder) && !autoencoder && (y %in% x)) {
    warning('removing response variable from the explanatory variables')
    x <- setdiff(x,y)
  }

  x_ignore <- setdiff(setdiff(cc, x), y)
  if(length(x_ignore) == 0L) x_ignore <- ''
  list(x=x, y=y, x_i=x_i, x_ignore=x_ignore, y_i=y_i)
}

.verify_datacols <- function(data, cols) {
  if(!is(data, "H2OFrame"))
    stop('`data` must be an H2OFrame object')
  if(!is.character(cols) && !is.numeric(cols))
    stop('`cols` must be column names or indices')

  cc <- colnames(data)
  if(length(cols) == 1L && cols == '')
    cols <- cc
  if(is.character(cols)) {
    if(!all(cols %in% cc))
      stop("Invalid column names: ", paste(cols[which(!cols %in% cc)], collapse=", "))
    cols_ind <- match(cols, cc)
  } else {
    if(any(cols < 1L | cols > length(cc)))
      stop('out of range explanatory variable ', paste(cols[cols < 1L | cols > length(cc)], collapse=','))
    cols_ind <- cols
    cols <- cc[cols_ind]
  }

  cols_ignore <- setdiff(cc, cols)
  if( length(cols_ignore) == 0L )
    cols_ignore <- ''
  list(cols=cols, cols_ind=cols_ind, cols_ignore=cols_ignore)
}

.build_cm <- function(cm, actual_names = NULL, predict_names = actual_names, transpose = TRUE) {
  categories <- length(cm)
  cf_matrix <- matrix(unlist(cm), nrow=categories)
  if(transpose)
    cf_matrix <- t(cf_matrix)

  cf_total <- apply(cf_matrix, 2L, sum)
  cf_error <- c(1 - diag(cf_matrix)/apply(cf_matrix,1L,sum), 1 - sum(diag(cf_matrix))/sum(cf_matrix))
  cf_matrix <- rbind(cf_matrix, cf_total)
  cf_matrix <- cbind(cf_matrix, round(cf_error, 3L))

  if(!is.null(actual_names))
    dimnames(cf_matrix) = list(Actual = c(actual_names, "Totals"), Predicted = c(predict_names, "Error"))
  cf_matrix
}

.seq_to_string <- function(vec = as.numeric(NA)) {
  vec <- sort(vec)
  if(length(vec) > 2L) {
    vec_diff <- diff(vec)
    if(abs(max(vec_diff) - min(vec_diff)) < .Machine$double.eps^0.5)
      return(paste(min(vec), max(vec), vec_diff[1], sep = ":"))
  }
  paste(vec, collapse = ",")
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
      e <- paste0(e, "argument \"", i$name, "\" is missing, with no default\n")
    else if( i$name %in% names(m) ) {
      # changing Java types to R types
      tryCatch(p_type <- .type.map[[i$type]], error = function(e) stop("Cannot find type ", i$type, " in .type.map."))
      switch(p_type, #create two type parameters for arrays
        "sarray" = p_type[2L] <- "character",
        "barray" = p_type[2L] <- "logical",
        "narray" = p_type[2L] <- "numeric")
      if(length(p_type) > 1L) {
        p_type <- p_type[2L]
        if(!inherits(m[[i$name]], p_type))
          e <- paste0(e, "array of ", i$name, " must be of type ", p_type, ", but got ", class(m[[i$name]]), ".\n")
        else
          m[[i$name]] <<- .collapse(m[[i$name]])
      } else if(!inherits(m[[i$name]], p_type))
        e <- paste0(e, " \"", i$name , "\" must be of type ", p_type, ", but got ", class(m[[i$name]]), ".\n")
      else if( length(i$values) > 1L)
        if( !(m[[i$name]] %in% i$values) ) {
          e <- paste0(e, "\"", i$name,"\" must be in")
          for(fact in i$values)
            e <- paste0(e, " \"", fact, "\",")
          e <- paste(e, "but got", m[[i$name]])
        }
      }
    e
  })

  if(any(nzchar(error)))
    stop(error)

  #---------- Create param list to pass ----------#
  p_val <- lapply(m, function(i) {
    if(is(i, "H2OFrame"))
      i@key
    else
      i
  })
  #---------- Verify Params ----------#
  rj <- .h2o.__remoteSend(client, method = "POST", paste0(.h2o.__MODEL_BUILDERS(algo), "/parameters"), .params = p_val)

  if(length(rj$validation_messages) != 0L)
    error <- lapply(rj$validation_messages, function(i) {
      if( !(i$message_type %in% c("HIDE","INFO")) )
        paste0(i$message, ".\n")
      else
        ""
    })
  if(any(nzchar(error)))
    stop(error)

  #---------- Return Params ----------#
  p_val
}

.run <- function(client, algo, m, envir) {
  m$training_frame <- get("training_frame", parent.frame())
  if( delete <- !((.is.eval(m$training_frame)))) .force.eval(ast = m$training_frame@ast, h2o.ID = m$training_frame@key)
  p_val <- .parms(client, algo, m, envir)

  res <- .h2o.__remoteSend(client, method = "POST", .h2o.__MODEL_BUILDERS(algo), .params = p_val)

  job_key <- res$job[[1L]]$key$name
  dest_key <- res$jobs[[1L]]$dest$name
  .h2o.__waitOnJob(client, job_key)
  # Grab model output and flatten one level
  res_model <- .h2o.__remoteSend(client, method = "GET", paste0(.h2o.__MODELS, "/", dest_key))
  res_model <- unlist(res_model, recursive = FALSE)
  res_model <- res_model$models

  if( delete ) h2o.rm(m$training_frame@key)
  .newModel(algo, res_model, client)
}

.newModel <- function(algo, json, client) do.call(.algo.map[[algo]], list(json, client))