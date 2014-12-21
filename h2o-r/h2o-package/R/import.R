#'
#' Data Import
#'
#' Importing data is a _lazy_ parse of the data. It adds an extra step so that a user may specify a variety of options
#' including a header file, separator type, and in the future column type. Additionally, the import phase provides
#' feedback on whether or not a folder or group of files may be imported together.

#' API ENDPOINT
.h2o.__IMPORT <- "ImportFiles.json"   # ImportFiles.json?path=/path/to/data

#'
#' Import a Folder of Files
#'
#' Import an entire directory of files. If the given path is relative, then it will be relative to the start location
#' of the H2O instance. The default behavior is to pass-through to the parse phase automatically.
h2o.importFolder <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  if(!is(object, "H2OConnection")) stop("`object` must be of class H2OConnection")
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  if(!is.character(pattern) || length(pattern) != 1L || is.na(pattern)) stop("`pattern` must be a character string")
  if(!is.character(key) || length(key) != 1L || is.na(key)) stop("`key` must be a character string")
  if(nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
    stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse) || length(parse) != 1L || is.na(parse))
    stop("`parse` must be TRUE or FALSE")

  res <- .h2o.__remoteSend(object, 'ImportFiles.json', path=path)
  if(length(res$fails) > 0L) {
    for(i in seq_len(length(res$fails)))
      cat(res$fails[[i]], "failed to import")
  }
  # Return only the files that successfully imported
  if(length(res$files) > 0L) {
    if(parse) {
      srcKey <- res$keys
      rawData <- new("H2ORawData", h2o=object, key=srcKey)
      ret <- h2o.parseRaw(data=rawData, key=key, header=header, sep=sep, col.names=col.names)
      h2o.rm(object, paste0("nfs:/", path))
      h2o.rm(object, paste0("nfs://private", path))
    } else {
      myData <- lapply(res$keys, function(x) new("H2ORawData", h2o=object, key=x))
      if(length(res$keys) == 1L)
        ret <- myData[[1L]]
      else
        ret <- myData
    }
  } else stop("all files failed to import")
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
  .Deprecated("h2o.importFolder")
  h2o.importFile(object, path, key, parse, header, sep, col.names)
}

#'
#' Import HDFS
#'
#' Import from an HDFS location.
h2o.importHDFS <- function(object, path, pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  .Deprecated("h2o.importFolder")
  h2o.importFolder(object, path, pattern, key, parse, header, sep, col.names)
}

#'
#' Upload Data
#'
#' Upload local files to the H2O instance.
h2o.uploadFile <- function(object, path, key = "", parse = TRUE, header, sep = "", col.names) {
  if(!is(object, "H2OConnection")) stop("`object` must be of class H2OConnection")
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  if(!is.character(key) || length(key) != 1L || is.na(key)) stop("`key` must be a character string")
  if(nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
    stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
  if(!is.logical(parse) || length(parse) != 1L || is.na(parse))
    stop("`parse` must be TRUE or FALSE")

  path <- normalizePath(path, winslash = "/")
  urlSuffix <- sprintf("PostFile.json?destination_key=%s", curlEscape(path))
  fileUploadInfo <- fileUpload(path)
  h2o.doSafePOST(conn = object, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = urlSuffix,
                 fileUploadInfo = fileUploadInfo)

  rawData <- new("H2ORawData", h2o=object, key=path)
  if (parse) {
    destination_key <- key
    h2o.parseRaw(data=rawData, key=destination_key, header=header, sep=sep, col.names=col.names)
  } else {
    rawData
  }
}
