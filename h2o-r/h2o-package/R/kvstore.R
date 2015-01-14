#-----------------------------------------------------------------------------------------------------------------------
# H2O Key-Value Store Functions
#-----------------------------------------------------------------------------------------------------------------------

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
h2o.ls <- function(conn = h2o.getConnection()) {
  ast <- new("ASTNode", root = new("ASTApply", op = "ls"))
  fr <- .newH2OObject("H2OFrame", ast=ast, key=.key.make(), h2o=conn, linkToGC = TRUE)
  ret <- as.data.frame(fr)
  h2o.rm(fr@key, fr@h2o)
  ret
}

#'
#' Remove All Keys on the H2O Cluster
#'
#' Removes the data from the h2o cluster, but does not remove the local references.
#'
#' @param conn An \linkS4class{H2OConnection} object containing the IP address and port number
#' of the H2O server.
#' @seealso \code{\link{h2o.rm}}
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' h2o.ls(localH2O)
#' h2o.removeAll(localH2O)
#' h2o.ls(localH2O)
h2o.removeAll<-
function(conn = h2o.getConnection()) {
  invisible(.h2o.__remoteSend(conn, .h2o.__REMOVEALL, method = "DELETE"))
}

#
#' Delete Objects In H2O
#'
#' Remove the h2o Big Data object(s) having the key name(s) from keys.
#'
#' @param keys The hex key associated with the object to be removed.
#' @param conn An \linkS4class{H2OConnection} object containing the IP address and port number of the H2O server.
#' @seealso \code{\link{h2o.assign}}, \code{\link{h2o.ls}}
h2o.rm <- function(keys, conn = h2o.getConnection()) {
  if (is(keys, "H2OConnection")) {
    temp <- keys
    keys <- conn
    conn <- temp
  }
  if(!is(conn, "H2OConnection")) stop("`conn` must be of class H2OConnection")
  if(!is.character(keys)) stop("`keys` must be of class character")

  for(i in seq_len(length(keys)))
    .h2o.__remoteSend(conn, .h2o.__REMOVE, key=keys[[i]], method = "DELETE")
}

#'
#' Garbage Collection of Temporary Frames
#'
#' @param conn An \linkS4class{H2OConnection} object containing the IP address and port number of the H2O server.

# TODO: This is an older version; need to go back through git and find the "good" one...
.h2o.gc <- function(conn = h2o.getConnection()) {
  frame_keys <- as.vector(h2o.ls()[,1L])
  frame_keys <- frame_keys[grepl(.get.session_id(), frame_keys)]
  # no reference? then destroy!
  f <- function(env) {
    l <- lapply(ls(env), function(x) {
      o <- get(x, envir=env)
      if(is(o, "H2OFrame") || is(o, "H2OModel")) o@key
    })
    Filter(Negate(is.null), l)
  }
  p_list  <- f(.pkg.env)
  g_list  <- f(globalenv())
  f1_list <- f(parent.frame())

  g_list <- unlist(c(p_list, g_list, f1_list))
  l <- setdiff(seq_len(length(frame_keys)),
               unlist(lapply(g_list, function(e) if (e %in% frame_keys) match(e, frame_keys) else NULL)))
  if (length(l) != 0L)
    h2o.rm(frame_keys[l])
  invisible(NULL)
}

#'
#' Rename an H2O object.
#'
#' Makes a copy of the data frame and gives it the desired the key.
#'
#' @param data An \linkS4class{H2OFrame} object
#' @param key The hex key to be associated with the H2O parsed data object
#'
h2o.assign <- function(data, key) {
  if(!is(data, "H2OFrame")) stop("`data` must be of class H2OFrame")
  if(!is.character(key) || length(key) != 1L || is.na(key)) stop("`key` must be a character string")
  if(key == data@key) stop("Destination key must differ from data key ", data@key)
  if (!grepl(.get.session_id(), key)) key <- paste0(key, .get.session_id())
  if (length(substitute(data)) > 1) {
    ID <- "tmp_value"
  } else {
    ID  <- deparse(substitute(data), width.cutoff = 500L)
  }
  ast <- .h2o.nary_op("rename", data, key)
  .force.eval(ast@ast, ID, parent.frame())
  data@key <- key
  data
#  o <- get(ID, parent.frame())
#  o@key <- key
#  o
}

#'
#' Get an R Reference to an H2O Dataset
#'
#' Get the reference to a frame with the given key in the H2O instance.
#'
#' @param key A string indicating the unique hex key of the dataset to retrieve.
#' @param conn \linkS4class{H2OConnection} object containing the IP address and port
#'             of the server running H2O.
#' @param linkToGC a logical value indicating whether to remove the underlying key
#'        from the H2O cluster when the R proxy object is garbage collected.
h2o.getFrame <- function(key, conn = h2o.getConnection(), linkToGC = FALSE) {
  if (is(key, "H2OConnection")) {
    temp <- key
    key <- conn
    conn <- temp
  }
  res <- .h2o.__remoteSend(conn, .h2o.__RAPIDS, ast=paste0("(%", key, ")"))
  cnames <- if( is.null(res$col_names) ) NA_character_ else res$col_names
  .h2o.parsedData(conn, key, res$num_rows, res$num_cols, cnames, linkToGC = linkToGC)
}

#' Get an R reference to an H2O model
#'
#' Returns a reference to an existing model in the H2O instance.
#'
#' @param key A string indicating the unique hex key of the model to retrieve.
#' @param conn \linkS4class{H2OConnection} object containing the IP address and port
#'             of the server running H2O.
#' @param linkToGC a logical value indicating whether to remove the underlying key
#'        from the H2O cluster when the R proxy object is garbage collected.
#' @return Returns an object that is a subclass of \linkS4class{H2OModel}.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#'
#' iris.hex <- as.h2o(iris, localH2O, "iris.hex")
#' key <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.hex)@@key
#' model.retrieved <- h2o.getModel(key, localH2O)
h2o.getModel <- function(key, conn = h2o.getConnection(), linkToGC = FALSE) {
  if (is(key, "H2OConnection")) {
    temp <- key
    key <- conn
    conn <- temp
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

        # Parse frame information to a key
        if (type == "H2OFrame") {
          toParse <- unlist(strsplit(value, split=","))
          key_toParse <- toParse[grep("\\\"name\\\"", toParse)]
          key <- unlist(strsplit(key_toParse[[1L]],split=":"))[2L]
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
          key <- unlist(strsplit(key_toParse[[1L]],split=":"))[2L]
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

  .newH2OObject(Class      = Class,
                h2o        = conn,
                key        = json$key$name,
                algorithm  = json$algo,
                parameters = parameters,
                model      = model,
                linkToGC   = linkToGC)
}
