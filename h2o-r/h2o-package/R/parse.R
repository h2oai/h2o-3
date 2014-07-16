h2o.assign <- function(data, key) {
  # if(class(data) != "H2OParsedData") stop("data must be of class H2OParsedData")
  if(!inherits(data, "H2OParsedData")) stop("data must be an H2O parsed dataset")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) == 0) stop("key cannot be an empty string")
  if(key == data@key) stop(paste("Destination key must differ from data key", data@key))

  res = .h2o.__exec2_dest_key(data@h2o, data@key, key)
  data@key = key
  return(data)
}

# ----------------------------------- File Parse Operations --------------------------------- #
h2o.parseRaw <- function(data, key = "", header, sep = "", col.names, version = 2) {
  if(version == 1)
    h2o.parseRaw.VA(data, key, header, sep, col.names)
  else if(version == 2)
    h2o.parseRaw.FV(data, key, header, sep, col.names)
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

h2o.parseRaw.VA <- function(data, key = "", header, sep = "", col.names) {
  if(class(data) != "H2ORawDataVA") stop("data must be of class H2ORawDataVA")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!(missing(header) || is.logical(header))) stop("header must be of class logical")
  if(!is.character(sep)) stop("sep must be of class character")
  if(!(missing(col.names) || class(col.names) == "H2OParsedDataVA")) stop(paste("col.names cannot be of class", class(col.names)))

  # If both header and column names missing, then let H2O guess if header exists
  sepAscii = ifelse(sep == "", sep, strtoi(charToRaw(sep), 16L))
  if(missing(header) && missing(col.names))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE, source_key=data@key, destination_key=key, separator=sepAscii)
  else if(missing(header) && !missing(col.names))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE, source_key=data@key, destination_key=key, separator=sepAscii, header=1, header_from_file=col.names@key)
  else if(!missing(header) && missing(col.names))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE, source_key=data@key, destination_key=key, separator=sepAscii, header=as.numeric(header))
  else
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE, source_key=data@key, destination_key=key, separator=sepAscii, header=as.numeric(header), header_from_file=col.names@key)

  # on.exit(.h2o.__cancelJob(data@h2o, res$response$redirect_request_args$job))
  .h2o.__waitOnJob(data@h2o, res$response$redirect_request_args$job)
  parsedData = new("H2OParsedDataVA", h2o=data@h2o, key=res$destination_key)
}

h2o.parseRaw.FV <- function(data, key = "", header, sep = "", col.names) {
  if(class(data) != "H2ORawData") stop("data must be of class H2ORawData")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!(missing(header) || is.logical(header))) stop(paste("header cannot be of class", class(header)))
  if(!is.character(sep)) stop("sep must be of class character")
  if(!(missing(col.names) || class(col.names) == "H2OParsedData")) stop(paste("col.names cannot be of class", class(col.names)))

  # If both header and column names missing, then let H2O guess if header exists
  sepAscii = ifelse(sep == "", sep, strtoi(charToRaw(sep), 16L))
  if(missing(header) && missing(col.names))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE2, source_key=data@key, destination_key=key, separator=sepAscii)
  else if(missing(header) && !missing(col.names))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE2, source_key=data@key, destination_key=key, separator=sepAscii, header=1, header_from_file=col.names@key)
  else if(!missing(header) && missing(col.names))
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE2, source_key=data@key, destination_key=key, separator=sepAscii, header=as.numeric(header))
  else
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_PARSE2, source_key=data@key, destination_key=key, separator=sepAscii, header=as.numeric(header), header_from_file=col.names@key)

  # on.exit(.h2o.__cancelJob(data@h2o, res$job_key))
  .h2o.__waitOnJob(data@h2o, res$job_key)
  parsedData = new("H2OParsedData", h2o=data@h2o, key=res$destination_key)
}