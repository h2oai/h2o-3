#-----------------------------------------------------------------------------------------------------------------------
# H2O Key-Value Store Functions
#-----------------------------------------------------------------------------------------------------------------------

.key.validate <- function(key) {
  if (!missing(key) && !is.null(key)) {
    if (!is.character(key) || length(key) != 1L || is.na(key)) {
      stop("`key` must be a character string")
    }
    if (nzchar(key)) {
      if (regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
        stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
      gc() # clean up KV store
    }
  }
  invisible(TRUE)
}

.key.make <- function(conn, prefix = "rapids") {
  if (conn@mutable$key_count == .Machine$integer.max) {
    conn@mutable$session_id <- .init.session_id(conn)
    conn@mutable$key_count  <- 0L
  }
  conn@mutable$key_count <- conn@mutable$key_count + 1L
  key <- sprintf("%s_%d", prefix, conn@mutable$key_count)  # removed session_id
  key
}

#'
#' List Keys on an H2O Cluster
#'
#' Accesses a list of object keys in the running instance of H2O.
#'
#' @param conn An \linkS4class{H2OConnection} object containing the IP address and port number of the H2O server.
#' @return Returns a list of hex keys in the current H2O instance.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' h2o.ls(localH2O)
#' @export
h2o.ls <- function(conn = h2o.getConnection()) {
  gc()
  ast <- new("ASTNode", root = new("ASTApply", op = "ls"))
  mutable <- new("H2OFrameMutableState", ast = ast)
  fr <- .newH2OFrame("H2OFrame", conn = conn, frame_id = .key.make(conn, "ls"), linkToGC = TRUE, mutable = mutable)
  ret <- as.data.frame(fr)
  h2o.rm(fr@frame_id, fr@conn)
  ret
}

#'
#' Remove All Objects on the H2O Cluster
#'
#' Removes the data from the h2o cluster, but does not remove the local references.
#'
#' @param conn An \linkS4class{H2OConnection} object containing the IP address and port number
#' of the H2O server.
#' @param timeout_secs Timeout in seconds. Default is no timeout.
#' @seealso \code{\link{h2o.rm}}
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' h2o.ls(localH2O)
#' h2o.removeAll(localH2O)
#' h2o.ls(localH2O)
#' @export
h2o.removeAll <- function(conn = h2o.getConnection(), timeout_secs=0) {
  tryCatch(
    invisible(.h2o.__remoteSend(conn, .h2o.__DKV, method = "DELETE", timeout=timeout_secs)),
    error = function(e) {
      print("Timeout on DELETE /DKV from R")
      print("Attempt thread dump...")
      h2o.killMinus3(conn)
      stop(e)
    })
}

#
#' Delete Objects In H2O
#'
#' Remove the h2o Big Data object(s) having the key name(s) from ids.
#'
#' @param ids The hex key associated with the object to be removed.
#' @param conn An \linkS4class{H2OConnection} object containing the IP address and port number of the H2O server.
#' @seealso \code{\link{h2o.assign}}, \code{\link{h2o.ls}}
#' @export
h2o.rm <- function(ids, conn = h2o.getConnection()) {
  if (is(ids, "H2OConnection")) {
    temp <- ids
    ids <- conn
    conn <- temp
  }
  if(!is(conn, "H2OConnection")) stop("`conn` must be of class H2OConnection")
  if( is(ids, "H2OFrame") ) ids <- ids@frame_id
  if(!is.character(ids)) stop("`ids` must be of class character")

  for(i in seq_len(length(ids)))
    .h2o.__remoteSend(conn, paste0(.h2o.__DKV, "/", ids[[i]]), method = "DELETE")
}

#'
#' Rename an H2O object.
#'
#' Makes a copy of the data frame and gives it the desired the key.
#'
#' @param data An \linkS4class{H2OFrame} object
#' @param key The hex key to be associated with the H2O parsed data object
#' @param deepCopy Should it do a deepCopy of the frame. Default is FALSE.
#'
#' @export
h2o.assign <- function(data, key, deepCopy=FALSE) {
  if(!is(data, "H2OFrame")) stop("`data` must be of class H2OFrame")
  t <- !.is.eval(data)
  if( t ) {
    tk <- data@frame_id
    .h2o.eval.frame(conn = data@conn, ast = data@mutable$ast, frame_id = tk)
  }

  .key.validate(key)
  if(key == data@frame_id) stop("Destination key must differ from input frame ", data@frame_id)
  expr <- NULL
  if( deepCopy ) {
    expr <- paste0("(= !", key, " %", data@frame_id, ")")   # this does a deepcopy!!
    res <- .h2o.raw_expr_op(expr, data, key=key, linkToGC=FALSE)
    .byref.update.frame(res)
  } else {
    expr <- paste0("(, (gput '", key, "' %", data@frame_id, ") (removeframe %",data@frame_id,"))")   # removes the original frame!
    res <- .h2o.raw_expr_op(expr, data, key=key, linkToGC=FALSE)
    .byref.update.frame(res)
  }
}

#'
#' Get an R Reference to an H2O Dataset
#'
#' Get the reference to a frame with the given frame_id in the H2O instance.
#'
#' @param frame_id A string indicating the unique frame of the dataset to retrieve.
#' @param conn \linkS4class{H2OConnection} object containing the IP address and port
#'             of the server running H2O.
#' @param linkToGC a logical value indicating whether to remove the underlying frame
#'        from the H2O cluster when the R proxy object is garbage collected.
#' @export
h2o.getFrame <- function(frame_id, conn = h2o.getConnection(), linkToGC = FALSE) {
  if (is(frame_id, "H2OConnection")) {
    temp <- frame_id
    frame_id <- conn
    conn <- temp
  }
  if( is.null(frame_id) ) return(NULL)
  res <- .h2o.__remoteSend(conn, paste0(.h2o.__FRAMES, "/", frame_id))$frames[[1]]
  cnames <- unlist(lapply(res$columns, function(c) c$label))
  .h2o.parsedData(conn, frame_id, res$rows, length(res$columns), cnames, linkToGC = linkToGC)
}

#' Get an R reference to an H2O model
#'
#' Returns a reference to an existing model in the H2O instance.
#'
#' @param model_id A string indicating the unique model_id of the model to retrieve.
#' @param conn \linkS4class{H2OConnection} object containing the IP address and port
#'             of the server running H2O.
#' @param linkToGC a logical value indicating whether to remove the underlying model
#'        from the H2O cluster when the R proxy object is garbage collected.
#' @return Returns an object that is a subclass of \linkS4class{H2OModel}.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#'
#' iris.hex <- as.h2o(iris, localH2O, "iris.hex")
#' model_id <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.hex)@@model_id
#' model.retrieved <- h2o.getModel(model_id, localH2O)
#' @export
h2o.getModel <- function(model_id, conn = h2o.getConnection(), linkToGC = FALSE) {
  if (is(model_id, "H2OConnection")) {
    temp <- model_id
    model_id <- conn
    conn <- temp
  }
  json <- .h2o.__remoteSend(conn, method = "GET", paste0(.h2o.__MODELS, "/", model_id))$models[[1L]]
  model_category <- json$output$model_category
  if (is.null(model_category))
    model_category <- "Unknown"
  else if (!(model_category %in% c("Unknown", "Binomial", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction")))
    stop(paste0("model_category, \"", model_category,"\", missing in the output"))
  Class <- paste0("H2O", model_category, "Model")
  model <- json$output[!(names(json$output) %in% c("__meta", "names", "domains", "model_category"))]
  MetricsClass <- paste0("H2O", model_category, "Metrics")
  # setup the metrics objects inside of model...
  model$training_metrics   <- new(MetricsClass, algorithm=json$algo, on_train=TRUE, metrics=model$training_metrics)
  model$validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE,metrics=model$validation_metrics)  # default is on_train=FALSE
  parameters <- list()
  allparams  <- list()
  lapply(json$parameters, function(param) {
    if (!is.null(param$actual_value)) {
      name <- param$name
      value <- param$actual_value
      mapping <- .type.map[param$type,]
      type    <- mapping[1L, 1L]
      scalar  <- mapping[1L, 2L]

      if (type == "numeric" && value == "Infinity")
        value <- Inf
      else if (type == "numeric" && value == "-Infinity")
        value <- -Inf

      # Parse frame information to a key
      if (type == "H2OFrame")
        value <- value$name
      # Parse model information to a key
      if (type == "H2OModel") {
        value <- value$name
      }

      # Response column needs to be parsed
      if (name == "response_column")
        value <- value$column_name
      allparams[[name]] <<- value
      # Store only user changed parameters into parameters
      # TODO: Should we use !isTrue(all.equal(param$default_value, param$actual_value)) instead?
      if (is.null(param$default_value) || param$required || !identical(param$default_value, param$actual_value))
        parameters[[name]] <<- value
    }
  })

  # Convert ignored_columns/response_column to valid R x/y
  cols <- colnames(h2o.getFrame(conn, parameters$training_frame))

  parameters$x <- setdiff(cols, parameters$ignored_columns)
  allparams$x <- setdiff(cols, allparams$ignored_columns)
  if (!is.null(parameters$response_column))
  {
    parameters$y <- parameters$response_column
    allparams$y <- allparams$response_column
    parameters$x <- setdiff(parameters$x, parameters$y)
    allparams$x <- setdiff(allparams$x, allparams$y)
  }

  allparams$ignored_columns <- NULL
  allparams$response_column <- NULL
  parameters$ignored_columns <- NULL
  parameters$response_column <- NULL
  .newH2OModel(Class          = Class,
                conn          = conn,
                model_id      = json$model_id$name,
                algorithm     = json$algo,
                parameters    = parameters,
                allparameters = allparams,
                model         = model,
                linkToGC      = linkToGC)
}


#'
#' Download the Scoring POJO of An H2O Model
#'
#' @param model An H2OModel
#' @param path The path to the directory to store the POJO (no trailing slash). If "", then print to console.
#'             The file name will be a compilable java file name.
#' @param conn An H2OClient object.
#' @return If path is "", then pretty print the POJO to the console.
#'         Otherwise save it to the specified directory.
#' @examples
#' library(h2o)
#' h <- h2o.init(nthreads=-1)
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x=1:4, y=5, training_frame=fr)
#'
#' h2o.download_pojo(my_model)  # print the model to screen
#' # h2o.download_pojo(my_model, getwd())  # save to the current working directory, NOT RUN
#' @export
h2o.download_pojo <- function(model, path="", conn=h2o.getConnection()) {
  model_id <- model@model_id
  java <- .h2o.__remoteSend(conn, method = "GET", paste0(.h2o.__MODELS, ".java/", model_id), raw=TRUE)
  file.path <- paste0(path, "/", model_id, ".java")
  if( path == "" ) cat(java)
  else write(java, file=file.path)

  if( path!="") print( paste0("POJO written to: ", file.path) )
}