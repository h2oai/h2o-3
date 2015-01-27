#'
#' H2O Data Parsing
#'
#' The second phase in the data ingestion step.

#'
#' Parse the Raw Data produced by the import phase.
h2o.parseRaw <- function(data, key = "", header, sep = "", col.names) {
  if(!is(data, "H2ORawData")) stop("`data` must be an H2ORawData object")
  .key.validate(key)
  if(!(missing(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
  if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")
  if(!(missing(col.names) || is(col.names, "H2OFrame"))) stop("`col.names` cannot be of class ", class(col.names))

  # Prep srcs: must be of the form [src1,src2,src3,...]
  srcs <- data@key
  srcs <- .collapse.char(srcs)

  # First go through ParseSetup
  parseSetup <- .h2o.__remoteSend(data@conn, .h2o.__PARSE_SETUP, srcs = srcs, method = "POST")
  ncols <- parseSetup$ncols
  col.names <- parseSetup$columnNames
  parsedSrcs <- sapply(parseSetup$srcs, function(asrc) asrc$name)

  if (!nzchar(key))
    key <- .key.make(data@conn, parseSetup$hexName)
  parse.params <- list(
        srcs = .collapse.char(parsedSrcs),
        hex  = key,
        columnNames = .collapse.char(col.names),
        sep = parseSetup$sep,
        pType = parseSetup$pType,
        ncols = ncols,
        checkHeader = parseSetup$checkHeader,
        singleQuotes = parseSetup$singleQuotes,
        delete_on_done = TRUE
        )

  # Perform the parse
  res <- .h2o.__remoteSend(data@conn, .h2o.__PARSE, method = "POST", .params = parse.params)
  hex <- res$job$dest$name

  # Poll on job
  .h2o.__waitOnJob(data@conn, res$job$key$name)
  # Return a new H2OFrame object
  h2o.getFrame(key=hex, linkToGC=TRUE)    
}

#'
#' Helper Collapse Function
#'
#' Collapse a character vector into a ','-sep array of the form: [thing1,thing2,...]
.collapse <- function(v) paste0('[', paste(v, collapse=','), ']')
.collapse.char <- function(v) paste0('[', paste0('"', v, '"', collapse=','), ']')

.h2o.fetchNRows <- function(conn = h2o.getConnection(), key) {
  .h2o.__remoteSend(conn, paste0(.h2o.__INSPECT, "?key=", key))$schema$rows
}

#'
#' The H2OFrame Constructor
.h2o.parsedData <- function(conn = h2o.getConnection(), key, nrows, ncols, col_names, linkToGC = TRUE) {
  mutable <- new("H2OFrameMutableState", nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OObject("H2OFrame", conn=conn, key=key, mutable=mutable, linkToGC=linkToGC)
}

#'
#' Create new H2OFrame object for predictions
.h2o.parsedPredData <- function(conn = h2o.getConnection(), predictions, linkToGC = TRUE) {
  key <- predictions$key$name
  col_names <- sapply(predictions$columns, function(column) column$label)
  nrows <- predictions$rows
  ncols <- length(col_names)
  mutable <- new("H2OFrameMutableState", nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OObject("H2OFrame", conn=conn, key=key, mutable=mutable, linkToGC=linkToGC)
}
