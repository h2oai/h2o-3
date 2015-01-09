# ------------------------------- Helper Functions --------------------------- #
# Used to verify data, x, y and turn into the appropriate things
.verify_dataxy <- function(data, x, y, autoencoder = FALSE) {
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

  if(!autoencoder && (y %in% x)) {
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

.run <- function(client, algo, params, envir) {
  params$training_frame <- get("training_frame", parent.frame())

  #---------- Force evaluate temporary ASTs ----------#
  delete_train <- !.is.eval(params$training_frame)
  if (delete_train) {
    temp_train_key <- params$training_frame@key
    .force.eval(ast = params$training_frame@ast, h2o.ID = temp_train_key)
  }
  if (!is.null(params$validation_frame)){
    params$validation_frame <- get("validation_frame", parent.frame())
    delete_valid <- !.is.eval(params$validation_frame)
    if (delete_valid) {
      temp_valid_key <- params$validation_frame@key
      .force.eval(ast = params$validation_frame@ast, h2o.ID = temp_valid_key)
    }
  }

  ALL_PARAMS <- .h2o.__remoteSend(client, method = "GET", .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]]$parameters

  params <- lapply(as.list(params), function(i) {
                     if (is.name(i))    i <- get(deparse(i), envir)
                     if (is.call(i))    i <- eval(i, envir)
                     if (is.integer(i)) i <- as.numeric(i)
                     i
                   })

  #---------- Check user parameter types ----------#
  error <- lapply(ALL_PARAMS, function(i) {
    e <- ""
    if (i$required && !(i$name %in% names(params)))
      e <- paste0("argument \"", i$name, "\" is missing, with no default\n")
    else if (i$name %in% names(params)) {
      # changing Java types to R types
      mapping <- .type.map[i$type,]
      type    <- mapping[1L, 1L]
      scalar  <- mapping[1L, 2L]
      if (is.na(type))
        stop("Cannot find type ", i$type, " in .type.map")
      if (scalar) {
        if (!inherits(params[[i$name]], type))
          e <- paste0("\"", i$name , "\" must be of type ", type, ", but got ", class(params[[i$name]]), ".\n")
        else if ((length(i$values) > 1L) && !(params[[i$name]] %in% i$values)) {
          e <- paste0("\"", i$name,"\" must be in")
          for (fact in i$values)
            e <- paste0(e, " \"", fact, "\",")
          e <- paste(e, "but got", params[[i$name]])
        }
      } else {
        if (!inherits(params[[i$name]], type))
          e <- paste0("vector of ", i$name, " must be of type ", type, ", but got ", class(params[[i$name]]), ".\n")
        else
          params[[i$name]] <<- .collapse(params[[i$name]])
      }
    }
    e
  })
  if(any(nzchar(error)))
    stop(error)

  #---------- Create parameter list to pass ----------#
  param_values <- lapply(params, function(i) {
    if(is(i, "H2OFrame"))
      i@key
    else
      i
  })

  #---------- Validate parameters ----------#
  validation <- .h2o.__remoteSend(client, method = "POST", paste0(.h2o.__MODEL_BUILDERS(algo), "/parameters"), .params = param_values)
  if(length(validation$validation_messages) != 0L) {
    error <- lapply(validation$validation_messages, function(i) {
      if( !(i$message_type %in% c("HIDE","INFO")) )
        paste0(i$message, ".\n")
      else
        ""
    })
    if(any(nzchar(error)))
      stop(error)
  }

  res <- .h2o.__remoteSend(client, method = "POST", .h2o.__MODEL_BUILDERS(algo), .params = param_values)

  job_key  <- res$job[[1L]]$key$name
  dest_key <- res$jobs[[1L]]$dest$name
  .h2o.__waitOnJob(client, job_key)

  # Grab model output and flatten one level
  res_model <- .h2o.__remoteSend(client, method = "GET", paste0(.h2o.__MODELS, "/", dest_key))
  res_model <- unlist(res_model, recursive = FALSE)
  res_model <- res_model$models

  if (delete_train) 
    h2o.rm(temp_train_key)
  if (!is.null(params$validation_frame))
    if (delete_valid)
      h2o.rm(temp_valid_key)
  
  do.call(.algo.map[[algo]], list(res_model, client))
}
