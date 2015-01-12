#'
#' H2O Data Parsing
#'
#' The second phase in the data ingestion step.

#'
#' Parse the Raw Data produced by the import phase.
h2o.parseRaw <- function(data, key = "", header, sep = "", col.names) {
  if(!is(data, "H2ORawData")) stop("`data` must be an H2ORawData object")
  if(!is.character(key) || length(key) != 1L || is.na(key)) stop("`key` must be a character string")
  if(nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
    stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!(missing(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
  if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")
  if(!(missing(col.names) || is(col.names, "H2OFrame"))) stop("`col.names` cannot be of class ", class(col.names))

  # Prep srcs: must be of the form [src1,src2,src3,...]
  srcs <- data@key
  srcs <- .collapse(srcs)

  # First go through ParseSetup
  parseSetup <- .h2o.__remoteSend(data@h2o, .h2o.__PARSE_SETUP, srcs = srcs)

  ncols <- parseSetup$ncols
  col.names <- parseSetup$columnNames

  parse.params <- list(
        srcs = srcs,
        hex  = paste0(ifelse(nzchar(key), paste0(key, ".hex"), parseSetup$hexName), .get.session_id()),
        columnNames = .collapse(col.names),
        sep = parseSetup$sep,
        pType = parseSetup$pType,
        ncols = ncols,
        checkHeader = parseSetup$checkHeader,
        singleQuotes = parseSetup$singleQuotes
        )

  # Perform the parse
  res <- .h2o.__remoteSend(data@h2o, .h2o.__PARSE, method = "POST", .params = parse.params)
  hex <- res$job$dest$name

  # Poll on job
  .h2o.__waitOnJob(data@h2o, res$job$key$name)

  # Remove keys to unparsed data
  h2o.rm(res$srcs[[1L]]$name, data@h2o)

  # Return a new H2OFrame object
  nrows <- .h2o.fetchNRows(data@h2o, hex)
  .h2o.parsedData(data@h2o, hex, nrows, ncols, col.names)
}

#'
#' Helper Collapse Function
#'
#' Collapse a character vector into a ','-sep array of the form: [thing1,thing2,...]
.collapse <- function(v) paste0('[', paste(v, collapse=","), ']')

.h2o.fetchNRows <- function(conn = h2o.getConnection(), key) {
  .h2o.__remoteSend(conn, paste0(.h2o.__INSPECT, "?key=", key))$schema$rows
}

#'
#' Load H2O Model from HDFS or Local Disk
#'
#' Load a saved H2O model from disk.
h2o.loadModel <- function(object, path="") {
  if(!is(object, 'H2OConnection')) stop('`object` must be of class H2OConnection')
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  res <- .h2o.__remoteSend(object, .h2o.__PAGE_LoadModel, path = path)
  h2o.getModel(res$model$'_key', object)
}

#'
#' The H2OFrame Constructor
.h2o.parsedData <- function(conn = h2o.getConnection(), key, nrow, ncol, col_names)
  new("H2OFrame", h2o=conn, key=key, nrows=nrow, ncols=ncol, col_names=col_names)

#'
#' Create new H2OFrame object for predictions
.h2o.parsedPredData <- function(conn = h2o.getConnection(), predictions) {
  key <- predictions$key$name
  col_names <- sapply(predictions$columns, function(column) column$label)
  nrows <- predictions$rows
  ncols <- length(col_names)
  factors <- sapply(predictions$columns, function(column) column$type == "enum")
  names(factors) <- col_names
  factors <- as.data.frame(factors)
  new("H2OFrame", h2o = conn, key = key, col_names = col_names, nrows = nrows, ncols = ncols, factors = factors)
}
