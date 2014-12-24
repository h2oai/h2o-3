#'
#' H2O Data Parsing
#'
#' The second phase in the data ingestion step.

# API ENDPOINTS
.h2o.__PARSE_SETUP <- "ParseSetup.json"  # Sample Usage: ParseSetup?srcs=[nfs://asdfsdf..., nfs://...]
.h2o.__PARSE       <- "Parse.json"       # Sample Usage: Parse?srcs=[nfs://path/to/data]&hex=KEYNAME&pType=CSV&sep=44&ncols=5&checkHeader=0&singleQuotes=false&columnNames=[C1,%20C2,%20C3,%20C4,%20C5]

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
  parseSetup <- .h2o.__remoteSend(data@h2o, 'ParseSetup.json', srcs = srcs)

  ncols <- parseSetup$ncols
  col.names <- parseSetup$columnNames

  parse.params <- list(
        srcs = srcs,
        hex  = ifelse(nzchar(key), paste0(key, ".hex"), parseSetup$hexName),
        columnNames = .collapse(col.names),
        sep = parseSetup$sep,
        pType = parseSetup$pType,
        ncols = ncols,
        checkHeader = parseSetup$checkHeader,
        singleQuotes = parseSetup$singleQuotes
        )

  # Perform the parse
  res <- .h2o.__remoteSend(data@h2o, 'Parse.json', method = "POST", .params = parse.params)
  hex <- res$job$dest$name

  # Poll on job
  .h2o.__waitOnJob(data@h2o, res$job$key$name)

  # Remove keys to unparsed data
  h2o.rm(data@h2o, res$srcs[[1]]$name)

  # Return a new H2OFrame object
  nrows <- .h2o.fetchNRows(data@h2o, hex)
  o <- .h2o.parsedData(data@h2o, hex, nrows, ncols, col.names)
  .pkg.env[[o@key]] <- o
  o

  # If both header and column names missing, then let H2O guess if header exists
#  sepAscii <- ifelse(nzchar(sep), strtoi(charToRaw(sep), 16L), sep)
#  if(missing(header) && missing(col.names))
#  else if(missing(header) && !missing(col.names))
#    res = .h2o.__remoteSend(data@h2o, .h2o.__PARSE, source_key=data@key, destination_key=key, separator=sepAscii, header=1, header_from_file=col.names@key)
#  else if(!missing(header) && missing(col.names))
#    res = .h2o.__remoteSend(data@h2o, .h2o.__PARSE, source_key=data@key, destination_key=key, separator=sepAscii, header=as.numeric(header))
#  else
#    res = .h2o.__remoteSend(data@h2o, .h2o.__PARSE, source_key=data@key, destination_key=key, separator=sepAscii, header=as.numeric(header), header_from_file=col.names@key)

#  .h2o.parsedData(data@h2o,
#  .h2o.exec2(expr = res$destination_key, h2o = data@h2o, dest_key = res$destination_key)
}

#'
#' Helper Collapse Function
#'
#' Collapse a character vector into a ','-sep array of the form: [thing1,thing2,...]
.collapse <- function(v) paste0('[', paste(v, collapse=","), ']')

#Inspect.json?key

.h2o.fetchNRows <- function(h2o, key) .h2o.__remoteSend(h2o, paste0('Inspect.json?key=', key))$schema$rows

#'
#' Load H2O Model from HDFS or Local Disk
#'
#' Load a saved H2O model from disk.
h2o.loadModel <- function(object, path="") {
  if(!is(object, 'H2OConnection')) stop('`object` must be of class H2OConnection')
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  res <- .h2o.__remoteSend(object, .h2o.__PAGE_LoadModel, path = path)
  h2o.getModel(object, res$model$'_key')
}

#'
#' The H2OFrame Constructor
.h2o.parsedData <- function(h2o, key, nrow, ncol, col_names)
  new("H2OFrame", h2o=h2o, key=key, nrows=nrow, ncols=ncol, col_names=col_names)

#'
#' Create new H2OFrame object for predictions
.h2o.parsedPredData<-
function(client, predictions) {
  key <- predictions$key$name
  col_names <- sapply(predictions$columns, function(column) column$label)
  nrows <- predictions$rows
  ncols <- length(col_names)
  factors <- sapply(predictions$columns, function(column) column$type == "enum")
  names(factors) <- col_names
  factors <- as.data.frame(factors)
  o <- new("H2OFrame", h2o = client, key = key, col_names = col_names, nrows = nrows, ncols = ncols, factors = factors)
  .pkg.env[[o@key]] <- o
  o
}
