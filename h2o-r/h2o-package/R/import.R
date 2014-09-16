#'
#' Data Import
#'
#' Importing data is a _lazy_ parse of the data. It adds an extra step so that a user may specify a variety of options
#' including a header file, separator type, and in the future column type. Additionally, the import phase provides
#' feedback on whether or not a folder or group of files may be imported together.

#'
#' Import a Folder of Files
#'
#' Import an entire directory of files. If the given path is relative, then it will be relative to the start location
#' of the H2O instance. The default behavior is to pass-through to the parse phase automatically.
h2o.importFolder <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")
  if(!is.character(pattern)) stop("pattern must be of class character")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1] == -1)
    stop("key must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse)) stop("parse must be of class logical")

  res <- .h2o.__remoteSend(object, .h2o.__IMPORT, path=path)
  if(length(res$fails) > 0) {
    for(i in 1:length(res$fails))
      cat(res$fails[[i]], "failed to import")
  }
  ret <- NULL
  # Return only the files that successfully imported
  if(length(res$files) > 0) {
    if(parse) {
      if(substr(path, nchar(path), nchar(path)) == .Platform$file.sep)
        path <- substr(path, 1, nchar(path)-1)
      regPath <- paste(path, pattern, sep=.Platform$file.sep)
      srcKey <- ifelse(length(res$keys) == 1, res$keys[[1]], paste("*", regPath, "*", sep=""))
      rawData <- new("H2ORawData", h2o=object, key=srcKey)
      assign("dd", rawData, globalenv())
      ret <- h2o.parseRaw(data=rawData, key=key, header=header, sep=sep, col.names=col.names)
    } else {
      myData = lapply(res$keys, function(x) { new("H2ORawData", h2o=object, key=x) })
      if(length(res$keys) == 1) ret <- myData[[1]] else ret <- myData
    }
  } else stop("All files failed to import!")
  h2o.rm(object, "nfs:/" %<p0-% path)
  invisible(h2o.rm(object, "nfs://private" %<p0-% path))
  ret
}

#'
#' Import A File
#'
#' Import a single file. If the given path is relative, then it will be relative to the start location
#' of the H2O instance. The default behavior is to pass-through to the parse phase automatically.
h2o.importFile <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  h2o.importFolder(object, path, pattern = "", key, parse, header, sep, col.names)
}

#'
#' Import A URL
#'
#' Import a data source from a URL.
h2o.importURL <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  print("This function has been deprecated in FluidVecs. In the future, please use h2o.importFile with a http:// prefix instead.")
  h2o.importFile(object, path, key, parse, header, sep, col.names)
}

#'
#' Import HDFS
#'
#' Import from an HDFS location.
h2o.importHDFS <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  print("This function has been deprecated in FluidVecs. In the future, please use h2o.importFolder with a hdfs:// prefix instead.")
  h2o.importFolder(object, path, pattern, key, parse, header, sep, col.names)
}

#'
#' Upload Data
#'
#' Upload local files to the H2O instance.
h2o.uploadFile <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names, silent = TRUE) {
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
  if(parse) parsedData = h2o.parseRaw(data=rawData, key=key, header=header, sep=sep, col.names=col.names) else rawData
}
