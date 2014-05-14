# Unique methods to H2O
# H2O client management operations

.readableTime <- function(epochTimeMillis) {
  days = epochTimeMillis/(24*60*60*1000)
  hours = (days-trunc(days))*24
  minutes = (hours-trunc(hours))*60
  seconds = (minutes-trunc(minutes))*60
  milliseconds = (seconds-trunc(seconds))*1000
  durationVector = trunc(c(days,hours,minutes,seconds,milliseconds))
  names(durationVector) = c("days","hours","minutes","seconds","milliseconds")
  if(length(durationVector[durationVector > 0]) > 1)
    showVec <- durationVector[durationVector > 0][1:2]
  else
    showVec <- durationVector[durationVector > 0]
  x1 = as.numeric(showVec)
  x2 = names(showVec)
  return(paste(x1,x2))
}

h2o.clusterInfo <- function(client) {
  if(missing(client) || class(client) != "H2OClient") stop("client must be a H2OClient object")
  myURL = paste("http://", client@ip, ":", client@port, "/", .h2o.__PAGE_CLOUD, sep = "")
  if(!url.exists(myURL)) stop("Cannot connect to H2O instance at ", myURL)

  res = NULL
  {
    res = fromJSON(postForm(myURL, style = "POST"))

    nodeInfo = res$nodes
    numCPU = sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

    if (numCPU == 0) {
      # If the cloud hasn't been up for a few seconds yet, then query again.
      # Sometimes the heartbeat info with cores and memory hasn't had a chance
      # to post it's information yet.
      threeSeconds = 3
      Sys.sleep(threeSeconds)
      res = fromJSON(postForm(myURL, style = "POST"))
    }
  }
  
  nodeInfo = res$nodes
  maxMem = sum(sapply(nodeInfo,function(x) as.numeric(x['max_mem_bytes']))) / (1024 * 1024 * 1024)
  numCPU = sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))
  clusterHealth =  all(sapply(nodeInfo,function(x) as.logical(x['num_cpus']))==TRUE)
  
  cat("R is connected to H2O cluster:\n")
  cat("    H2O cluster uptime:       ", .readableTime(as.numeric(res$cloud_uptime_millis)), "\n")
  cat("    H2O cluster version:      ", res$version, "\n")
  cat("    H2O cluster name:         ", res$cloud_name, "\n")
  cat("    H2O cluster total nodes:  ", res$cloud_size, "\n")
  cat("    H2O cluster total memory: ", sprintf("%.2f GB", maxMem), "\n")
  cat("    H2O cluster total cores:  ", numCPU, "\n")
  cat("    H2O cluster healthy:      ", clusterHealth, "\n")  
}

h2o.ls <- function(object, pattern = "") {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(pattern)) stop("pattern must be of class character")
  
  i = 0
  myList = list()
  page_keys = .MAX_INSPECT_ROW_VIEW
  
  # Need to pull all keys from every page in StoreView
  while(page_keys == .MAX_INSPECT_ROW_VIEW) {
    res = .h2o.__remoteSend(object, .h2o.__PAGE_VIEWALL, filter=pattern, offset=i*.MAX_INSPECT_ROW_VIEW, view=.MAX_INSPECT_ROW_VIEW)
    if(length(res$keys) == 0) return(myList)
    temp = lapply(res$keys, function(y) c(y$key, y$value_size_bytes))
    
    i = i + 1
    myList = c(myList, temp)
    page_keys = res$num_keys
  }
  tot_keys = page_keys + (i-1)*.MAX_INSPECT_ROW_VIEW
  
  temp = data.frame(matrix(unlist(myList), nrow=tot_keys, ncol=2, byrow = TRUE))
  colnames(temp) = c("Key", "Bytesize")
  temp$Key = as.character(temp$Key)
  temp$Bytesize = as.numeric(as.character(temp$Bytesize))
  return(temp)
}

h2o.rm <- function(object, keys) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(keys)) stop("keys must be of class character")
  
  for(i in 1:length(keys))
    .h2o.__remoteSend(object, .h2o.__PAGE_REMOVE, key=keys[[i]])
}

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

# ----------------------------------- File Import Operations --------------------------------- #
# WARNING: You must give the FULL file/folder path name! Relative paths are taken with respect to the H2O server directory
# ----------------------------------- Import Folder --------------------------------- #
h2o.importFolder <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names, version = 2) {
  if(version == 1)
    h2o.importFolder.VA(object, path, pattern, key, parse, header, sep, col.names)
  else if(version == 2)
    h2o.importFolder.FV(object, path, pattern, key, parse, header, sep, col.names)
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

h2o.importFolder.VA <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")
  
  res = .h2o.__remoteSend(object, .h2o.__PAGE_IMPORTFILES, path=path)
  if(length(res$fails) > 0) {
    for(i in 1:length(res$fails)) 
      cat(res$fails[[i]], "failed to import")
  }
  
  # Return only the files that successfully imported
  if(length(res$files) > 0) {
    if(parse) {
      if(substr(path, nchar(path), nchar(path)) == .Platform$file.sep)
        path <- substr(path, 1, nchar(path)-1)
      regPath = paste(path, pattern, sep=.Platform$file.sep)
      srcKey = ifelse(length(res$keys) == 1, res$keys[1], paste("*", regPath, "*", sep=""))
      rawData = new("H2ORawDataVA", h2o=object, key=srcKey)
      h2o.parseRaw.VA(data=rawData, key=key, header=header, sep=sep, col.names=col.names)
    } else {
      myData = lapply(res$keys, function(x) { new("H2ORawDataVA", h2o=object, key=x) })
      if(length(res$keys) == 1) myData[[1]] else myData
    }
  } else stop("All files failed to import!")
}
  
h2o.importFolder.FV <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(pattern)) stop("pattern must be of class character")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")
  
  # if(!file.exists(path)) stop("Directory does not exist!")
  # res = .h2o.__remoteSend(object, .h2o.__PAGE_IMPORTFILES2, path=normalizePath(path))
  res = .h2o.__remoteSend(object, .h2o.__PAGE_IMPORTFILES2, path=path)
  if(length(res$fails) > 0) {
    for(i in 1:length(res$fails)) 
      cat(res$fails[[i]], "failed to import")
  }
  
  # Return only the files that successfully imported
  if(length(res$files) > 0) {
    if(parse) {
      if(substr(path, nchar(path), nchar(path)) == .Platform$file.sep)
        path <- substr(path, 1, nchar(path)-1)
      regPath = paste(path, pattern, sep=.Platform$file.sep)
      srcKey = ifelse(length(res$keys) == 1, res$keys[[1]], paste("*", regPath, "*", sep=""))
      rawData = new("H2ORawData", h2o=object, key=srcKey)
      h2o.parseRaw.FV(data=rawData, key=key, header=header, sep=sep, col.names=col.names) 
    } else {
      myData = lapply(res$keys, function(x) { new("H2ORawData", h2o=object, key=x) })
      if(length(res$keys) == 1) myData[[1]] else myData
    }
  } else stop("All files failed to import!")
}

# ----------------------------------- Import File --------------------------------- #
h2o.importFile <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names, version = 2) {
  if(version == 1)
    h2o.importFile.VA(object, path, key, parse, header, sep, col.names)
  else if(version == 2)
    h2o.importFile.FV(object, path, key, parse, header, sep, col.names)
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

h2o.importFile.VA <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  h2o.importFolder.VA(object, path, pattern = "", key, parse, header, sep, col.names)
  # if(missing(key) || nchar(key) == 0)
  #  h2o.importFolder.VA(object, path, pattern = "", key = "", parse, header, sep, col.names = col.names)
  # else
  #  h2o.importURL.VA(object, paste("file:///", path, sep=""), key, parse, header, sep, col.names = col.names)
}

h2o.importFile.FV <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  h2o.importFolder.FV(object, path, pattern = "", key, parse, header, sep, col.names)
}

# ----------------------------------- Import URL --------------------------------- #
h2o.importURL <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names, version = 2) {
  if(version == 1)
    h2o.importURL.VA(object, path, key, parse, header, sep, col.names)
  else if(version == 2)
    h2o.importURL.FV(object, path, key, parse, header, sep, col.names)
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

h2o.importURL.VA <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")
  
  destKey = ifelse(parse, "", key)
  res = .h2o.__remoteSend(object, .h2o.__PAGE_IMPORTURL, url=path, key=destKey)
  rawData = new("H2ORawDataVA", h2o=object, key=res$key)
  if(parse) parsedData = h2o.parseRaw.VA(data=rawData, key=key, header=header, sep=sep, col.names=col.names) else rawData
}

h2o.importURL.FV <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  print("This function has been deprecated in FluidVecs. In the future, please use h2o.importFile.FV with a http:// prefix instead.")
  h2o.importFile.FV(object, path, key, parse, header, sep, col.names)
}

# ----------------------------------- Import HDFS --------------------------------- #
h2o.importHDFS <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names, version = 2) {
  if(version == 1)
    h2o.importHDFS.VA(object, path, pattern, key, parse, header, sep, col.names)
  else if(version == 2)
    h2o.importHDFS.FV(object, path, pattern, key, parse, header, sep, col.names)
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

h2o.importHDFS.VA <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")
  
  res = .h2o.__remoteSend(object, .h2o.__PAGE_IMPORTHDFS, path=path)
  if(length(res$failed) > 0) {
    for(i in 1:res$num_failed) 
      cat(res$failed[[i]]$file, "failed to import")
  }
  
  # Return only the files that successfully imported
  if(res$num_succeeded > 0) {
    if(parse) {
      if(substr(path, nchar(path), nchar(path)) == .Platform$file.sep)
        path <- substr(path, 1, nchar(path)-1)
      regPath = paste(path, pattern, sep=.Platform$file.sep)
      srcKey = ifelse(res$num_succeeded == 1, res$succeeded[[1]]$key, paste("*", regPath, "*", sep=""))
      rawData = new("H2ORawDataVA", h2o=object, key=srcKey)
      h2o.parseRaw.VA(data=rawData, key=key, header=header, sep=sep, col.names=col.names) 
    } else {
      myData = lapply(res$succeeded, function(x) { new("H2ORawDataVA", h2o=object, key=x$key) })
      if(res$num_succeeded == 1) myData[[1]] else myData
    }
  } else stop("All files failed to import!")
}

h2o.importHDFS.FV <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  print("This function has been deprecated in FluidVecs. In the future, please use h2o.importFolder.FV with a hdfs:// prefix instead.")
  h2o.importFolder.FV(object, path, pattern, key, parse, header, sep, col.names)
}

# ----------------------------------- Upload File --------------------------------- #
h2o.uploadFile <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names, silent = TRUE, version = 2) {
  if(version == 1)
    h2o.uploadFile.VA(object, path, key, parse, header, sep, col.names, silent)
  else if(version == 2)
    h2o.uploadFile.FV(object, path, key, parse, header, sep, col.names, silent)
  else
    stop("version must be either 1 (ValueArray) or 2 (FluidVecs)")
}

h2o.uploadFile.VA <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names, silent = TRUE) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")
  if(!is.logical(silent)) stop("silent must be of class logical")
  
  url = paste("http://", object@ip, ":", object@port, "/PostFile.json", sep="")
  url = paste(url, "?key=", URLencode(path), sep="")
  if(file.exists(h2o.getLogPath("Command"))) .h2o.__logIt(url, NULL, "Command")
  if(silent)
    temp = postForm(url, .params = list(fileData = fileUpload(normalizePath(path))))
  else
    temp = postForm(url, .params = list(fileData = fileUpload(normalizePath(path))), .opts = list(verbose = TRUE))
  rawData = new("H2ORawDataVA", h2o=object, key=path)
  if(parse) parsedData = h2o.parseRaw.VA(data=rawData, key=key, header=header, sep=sep, col.names=col.names) else rawData
}

h2o.uploadFile.FV <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names, silent = TRUE) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")
  if(!is.logical(silent)) stop("silent must be of class logical")
  
  url = paste("http://", object@ip, ":", object@port, "/2/PostFile.json", sep="")
  url = paste(url, "?key=", URLencode(path), sep="")
  if(file.exists(h2o.getLogPath("Command"))) .h2o.__logIt(url, NULL, "Command")
  if(silent)
    temp = postForm(url, .params = list(fileData = fileUpload(normalizePath(path))))
  else
    temp = postForm(url, .params = list(fileData = fileUpload(normalizePath(path))), .opts = list(verbose = TRUE))
  rawData = new("H2ORawData", h2o=object, key=path)
  if(parse) parsedData = h2o.parseRaw.FV(data=rawData, key=key, header=header, sep=sep, col.names=col.names) else rawData
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
      
#-------------------------------- Miscellaneous -----------------------------------#
h2o.exportFile <- function(data, path, force = FALSE) {
    canHandle = FALSE
    if (class(data) == "H2OParsedData") { canHandle = TRUE }
    if (class(data) == "H2OParsedDataVA") { canHandle = TRUE }
    if (! canHandle) {
        stop("h2o.exportFile only works on H2OParsedData or H2OParsedDataVA frames")
    }
    if(!is.character(path)) stop("path must be of class character")
    if(nchar(path) == 0) stop("path must be a non-empty string")
    if(!is.logical(force)) stop("force must be of class logical")
    
    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_EXPORTFILES, src_key = data@key, path = path, force = as.numeric(force))
}

h2o.exportHDFS <- function(object, path) {
  if(inherits(object, "H2OModelVA")) stop("h2o.exportHDFS does not work under ValueArray")
  else if(!inherits(object, "H2OModel")) stop("object must be an H2O model")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  
  res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_EXPORTHDFS, source_key = object@key, path = path)
}

h2o.downloadCSV <- function(data, filename) {
  if( missing(data)) stop('Must specify data')
  if(! class(data) %in% c('H2OParsedDataVA', 'H2OParsedData'))
    stop('data is not an H2O data object')
  if( missing(filename) ) stop('Must specify filename')
  
  str <- paste('http://', data@h2o@ip, ':', data@h2o@port, '/2/DownloadDataset?src_key=', data@key, sep='')
  has_wget <- '' != Sys.which('wget')
  has_curl <- '' != Sys.which('curl')
  if( !(has_wget || has_curl)) stop("I can't find wget or curl on your system")
  if( has_wget ){
    cmd <- 'wget'
    args <- paste('-O', filename, str)
  } else {
    cmd <- 'curl'
    args <- paste('-o', filename, str)
  }
  
  print(paste('cmd:', cmd))
  print(paste('args:', args))
  val <- system2(cmd, args, wait=T)
  if( val != 0 )
    print(paste('Bad return val', val))
}

# ----------------------------------- Work in Progress --------------------------------- #
# setGeneric("h2o<-", function(x, value) { standardGeneric("h2o<-") })
# setMethod("h2o<-", signature(x="H2OParsedData", value="H2OParsedData"), function(x, value) {
#   res = .h2o.__exec2_dest_key(x@h2o, value@key, x@key); return(x)
# })
# 
# setMethod("h2o<-", signature(x="H2OParsedData", value="numeric"), function(x, value) {
#   res = .h2o.__exec2_dest_key(x@h2o, paste("c(", paste(value, collapse=","), ")", sep=""), x@key); return(x)
# })

# ----------------------- Log helper ----------------------- #
h2o.logAndEcho <- function(conn, message) {
  if(class(conn) != "H2OClient") stop("conn must be an H2OClient")
  if(!is.character(message)) stop("message must be a character string")
  
  res = .h2o.__remoteSend(conn, .h2o.__PAGE_LOG_AND_ECHO, message=message)
  echo_message = res$message
  return(echo_message)
}

h2o.downloadAllLogs <- function(client, dirname = ".", filename = NULL) {
  if(class(client) != "H2OClient") stop("client must be of class H2OClient")
  if(!is.character(dirname)) stop("dirname must be of class character")
  if(!is.null(filename)) {
    if(!is.character(filename)) stop("filename must be of class character")
    else if(nchar(filename) == 0) stop("filename must be a non-empty string")
  }
  
  url = paste("http://", client@ip, ":", client@port, "/", .h2o.__DOWNLOAD_LOGS, sep="")
  if(!file.exists(dirname)) dir.create(dirname)
  
  cat("Downloading H2O logs from server...\n")
  h = basicHeaderGatherer()
  tempfile = getBinaryURL(url, headerfunction = h$update, verbose = TRUE)
  
  # Get filename from HTTP header of response
  if(is.null(filename)) {
    # temp = strsplit(as.character(Sys.time()), " ")[[1]]
    # myDate = gsub("-", "", temp[1]); myTime = gsub(":", "", temp[2])
    # file_name = paste("h2ologs_", myDate, "_", myTime, ".zip", sep="")
    atch = h$value()[["Content-Disposition"]]
    ind = regexpr("filename=", atch)[[1]]
    if(ind == -1) stop("Header corrupted: Expected attachment filename in Content-Disposition")
    filename = substr(atch, ind+nchar("filename="), nchar(atch))
  }
  myPath = paste(normalizePath(dirname), filename, sep = .Platform$file.sep)
  
  cat("Writing H2O logs to", myPath, "\n")
  # download.file(url, destfile = myPath)
  writeBin(tempfile, myPath)
}

# ----------------------- Deprecated ----------------------- #
# h2o.checkClient <- function(object) {
#   if(class(object) != "H2OClient") stop("object must be of class H2OClient")
#   
#   myURL = paste("http://", object@ip, ":", object@port, sep="")
#   if(!url.exists(myURL)) {
#     print("H2O is not running yet, launching it now.")
#     h2o.startLauncher()
#     invisible(readline("Start H2O, then hit <Return> to continue: "))
#     if(!url.exists(myURL)) stop("H2O failed to start, stopping execution.")
#   } else { 
#     cat("Successfully connected to", myURL, "\n")
#     if("h2oRClient" %in% rownames(installed.packages()) && (pv=packageVersion("h2oRClient")) != (sv=.h2o.__version(object)))
#       warning(paste("Version mismatch! Server running H2O version", sv, "but R package is version", pv))
#   }
# }
# 
# h2o.startLauncher <- function() {
#   myOS = Sys.info()["sysname"]
#   
#   if(myOS == "Windows") verPath = paste(Sys.getenv("APPDATA"), "h2o", sep=.Platform$file.sep)
#   else verPath = paste(Sys.getenv("HOME"), "Library/Application Support/h2o", sep=.Platform$file.sep)
#   myFiles = list.files(verPath)
#   if(length(myFiles) == 0) stop("Cannot find location of H2O launcher. Please check that your H2O installation is complete.")
#   # TODO: Must trim myFiles so all have format 1.2.3.45678.txt (use regexpr)!
#   
#   # Get H2O with latest version number
#   # If latest isn't working, maybe go down list to earliest until one executes?
#   fileName = paste(verPath, tail(myFiles, n=1), sep=.Platform$file.sep)
#   myVersion = strsplit(tail(myFiles, n=1), ".txt")[[1]]
#   launchPath = readChar(fileName, file.info(fileName)$size)
#   if(is.null(launchPath) || launchPath == "")
#     stop(paste("No H2O launcher matching H2O version", myVersion, "found"))
#   
#   if(myOS == "Windows") {
#     tempPath = paste(launchPath, "windows/h2o.bat", sep=.Platform$file.sep)
#     if(!file.exists(tempPath)) stop(paste("Cannot open H2OLauncher.jar! Please check if it exists at", tempPath))
#     shell.exec(tempPath)
#   }
#   else {
#     tempPath = paste(launchPath, "Contents/MacOS/h2o", sep=.Platform$file.sep)
#     if(!file.exists(tempPath)) stop(paste("Cannot open H2OLauncher.jar! Please check if it exists at", tempPath))
#     system(paste("bash ", tempPath))
#   }
# }
