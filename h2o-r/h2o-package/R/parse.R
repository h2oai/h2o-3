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
  if(class(data) != "H2ORawData") stop("data must be of class H2ORawData")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!(missing(header) || is.logical(header))) stop(paste("header cannot be of class", class(header)))
  if(!is.character(sep)) stop("sep must be of class character")
  if(!(missing(col.names) || class(col.names) == "h2o.frame")) stop(paste("col.names cannot be of class", class(col.names)))

  # Prep srcs: must be of the form [src1,src2,src3,...]
  srcs <- c(data@key)
  srcs <- .collapse(srcs)

  # First go through ParseSetup
  parseSetup <- .h2o.__remoteSend(data@h2o, 'ParseSetup.json', srcs = srcs)

  ncols <- parseSetup$ncols
  col.names <- parseSetup$columnNames

  parse.params <- list(
        srcs = srcs,
        hex  = ifelse(key != "", key %p0% ".hex", parseSetup$hexName),
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

  # Return a new h2o.frame object
  nrows <- .h2o.fetchNRows(data@h2o, hex)
  o <- .h2o.parsedData(data@h2o, hex, nrows, ncols, col.names)
  .pkg.env[[o@key]] <- o
  o

  # If both header and column names missing, then let H2O guess if header exists
#  sepAscii <- ifelse(sep == "", sep, strtoi(charToRaw(sep), 16L))
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
.collapse<-
function(v) {
  v <- paste(v, collapse=",", sep =" ")
  v <- '[' %p0% v %p0% ']'
  v
}

#Inspect.json?key

.h2o.fetchNRows <- function(h2o, key) { .h2o.__remoteSend(h2o, 'Inspect.json?key=' %p0% key)$schema$rows }

#'
#' Load H2O Model from HDFS or Local Disk
#'
#' Load a saved H2O model from disk.
h2o.loadModel <- function(object, path="") {
  if(missing(object)) stop('Must specify object')
  if(class(object) != 'h2o.client') stop('object must be of class h2o.client')
  if(!is.character(path)) stop('path must be of class character')
  res <- .h2o.__remoteSend(object, .h2o.__PAGE_LoadModel, path = path)
  h2o.getModel(object, res$model$'_key')
}

#'
#' The h2o.frame Constructor
.h2o.parsedData <- function(h2o, key, nrow, ncol, col_names) new("h2o.frame", h2o=h2o, key=key, nrows=nrow, ncols=ncol, col_names=col_names)

#'
#' Create new h2o.frame object for predictions
.h2o.parsedPredData<-
function(client, predictions) {
  browser()
  key <- predictions$key$name
  col_names <- sapply(predictions$columns, function(column) column$label)
  nrows <- predictions$rows
  ncols <- length(col_names)
  factors <- sapply(predictions$columns, function(column) if(column$type == "enum") TRUE else FALSE )
  names(factors) <- col_names
  factors <- as.data.frame(factors)
  o <- new("h2o.frame", h2o = client, key = key, col_names = col_names, nrows = nrows, ncols = ncols, factors = factors)
  .pkg.env[[o@key]] <- o
  o
}
