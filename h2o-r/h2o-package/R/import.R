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
