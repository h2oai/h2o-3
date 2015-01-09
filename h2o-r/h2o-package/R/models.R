#'
#' Retrieve Model Data
#'
#' After a model is constructed by H2O, R must create a view of the model. All views are backed by S4 objects that
#' subclass the H2OModel.

#' Get an R reference to an H2O model
#'
#' Returns a reference to an existing model in the H2O instance.
#'
#' @param conn \linkS4class{H2OConnection} object containing the IP address and port
#'             of the server running H2O.
#' @param key A string indicating the unique hex key of the model to retrieve.
#' @return Returns an object that is a subclass of \linkS4class{H2OModel}.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#'
#' iris.hex <- as.h2o(localH2O, iris, "iris.hex")
#' key <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.hex)@@key
#' model.retrieved <- h2o.getMode(localH2O, key)
h2o.getModel <- function(conn, key)
{
  if (missing(key)) {
    # means h2o is the one that's missing... retrieve it!
    key <- conn
    conn <- .retrieveH2O(parent.frame())
  }

  json <- .h2o.__remoteSend(conn, method = "GET", paste0(.h2o.__MODELS, "/", key))$models[[1L]]
  model_category <- json$output$model_category
  if (is.null(model_category))
     model_category <- "Unknown"
  else if (!(model_category %in% c("Unknown", "Binomial", "Multinomial", "Regression", "Clustering")))
    stop("model_category missing in the output")
  Class <- paste0("H2O", model_category, "Model")
  model <- json$output[!(names(json$output) %in% c("__meta", "names", "domains", "model_category"))]
  parameters <- list()
  lapply(json$parameters, function(param) {
    if (!is.null(param$actual_value))
    {
      name <- param$name
      if (is.null(param$default_value) || param$default_value != param$actual_value){
        value <- param$actual_value
        mapping <- .type.map[param$type,]
        type    <- mapping[1L, 1L]
        scalar  <- mapping[1L, 2L]
        
        # Change Java Array to R list
        if (!scalar) {
          arr <- gsub("\\[", "", gsub("]", "", value))
          value <- unlist(strsplit(arr, split=", "))
        }
        
        # Prase frame information to a key
        if (type == "H2OFrame") {
          toParse <- unlist(strsplit(value, split=","))
          key_toParse <- toParse[grep("\\\"name\\\"", toParse)]
          key <- unlist(strsplit(key_toParse[[1]],split=":"))[2]
          value <- gsub("\\\"", "", key)
        } else if (type == "numeric")
          value <- as.numeric(value)
        else if (type == "logical")
          value <- as.logical(value)
        
        # Response column needs to be parsed
        if (name == "response_column")
        {
          toParse <- unlist(strsplit(value, split=","))
          key_toParse <- toParse[grep("\\\"column_name\\\"", toParse)]
          key <- unlist(strsplit(key_toParse[[1]],split=":"))[2]
          value <- gsub("\\\"", "", key)
        }
        parameters[[name]] <<- value
      }
    }
  })
  
  # Convert ignored_columns/response_column to valid R x/y
  if (!is.null(parameters$ignored_columns))
    parameters$x <- .verify_datacols(h2o.getFrame(conn, parameters$training_frame), parameters$ignored_columns)$cols_ignore
  if (!is.null(parameters$response_column))
  {
    parameters$y <- parameters$response_column
    parameters$x <- setdiff(parameters$x, parameters$y)
  }
  
  parameters$ignored_columns <- NULL
  parameters$response_column <- NULL
  
  new(Class      = Class,
      h2o        = conn,
      key        = json$key$name,
      algorithm  = json$algo,
      parameters = parameters,
      model      = model)
}

#' Cross Validate an H2O Model
h2o.crossValidate <- function(model, nfolds, model.type = c("gbm", "glm", "deeplearning"), params, strategy = c("mod1", "random"), ...)
{
  output <- data.frame()
  dots <- list(...)
  
  for(type in dots)
    if (is.environment(type))
    {
      dots$envir <- type
      type <- NULL
    }
  if (is.null(dots$envir)) 
    dots$envir <- parent.frame()
#   params$envir <- l$envir

  if( nfolds < 2 ) stop("`nfolds` must be greater than or equal to 2")
  if( missing(model) & missing(model.type) ) stop("must declare `model` or `model.type`")
  else if( missing(model) )
  {
    if(model.type == "gbm") model.type = "h2o.gbm"
    else if(model.type == "glm") model.type = "h2o.glm"
    else if(model.type == "deeplearning") model.type = "h2o.deeplearning"
    
    model <- do.call(model.type, c(params, envir = dots$envir))
  }
  output[1, "fold_num"] <- -1
  output[1, "model_key"] <- model@key
  # output[1, "model"] <- model@model$mse_valid
  
  data <- params$training_frame
  data <- eval(data, dots$envir)
  data.len <- nrow(data)

  # nfold_vec <- h2o.sample(fr, 1:nfolds)
  nfold_vec <- sample(rep(1:nfolds, length.out = data.len), data.len)

  fnum_id <- as.h2o(conn, nfold_vec)
  fnum_id <- h2o.cbind(fnum_id, data)

  xval <- lapply(1:nfolds, function(i) {
      params$training_frame <- data[fnum_id$object != i, ]
      params$validation_frame <- data[fnum_id$object != i, ]
      fold <- do.call(model.type, c(params, envir = dots$envir))
      output[(i+1), "fold_num"] <<- i - 1
      output[(i+1), "model_key"] <<- fold@key
      # output[(i+1), "cv_err"] <<- mean(as.vector(fold@model$mse_valid))
      fold
    })
  print(output)
  
  model
}

