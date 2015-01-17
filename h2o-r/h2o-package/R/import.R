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
h2o.importFolder <- function(path, conn = h2o.getConnection(), pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  if (is(path, "H2OConnection")) {
    temp <- path
    path <- conn
    conn <- temp
  }
  if(!is(conn, "H2OConnection")) stop("`conn` must be of class H2OConnection")
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  if(!is.character(pattern) || length(pattern) != 1L || is.na(pattern)) stop("`pattern` must be a character string")
  .key.validate(key)
  if(!is.logical(parse) || length(parse) != 1L || is.na(parse))
    stop("`parse` must be TRUE or FALSE")

  res <- .h2o.__remoteSend(conn, .h2o.__IMPORT, path=path)
  if(length(res$fails) > 0L) {
    for(i in seq_len(length(res$fails)))
      cat(res$fails[[i]], "failed to import")
  }
  # Return only the files that successfully imported
  if(length(res$files) > 0L) {
    if(parse) {
      srcKey <- res$keys
      rawData <- .newH2OObject("H2ORawData", conn=conn, key=srcKey, linkToGC=TRUE)
      ret <- h2o.parseRaw(data=rawData, key=key, header=header, sep=sep, col.names=col.names)
    } else {
      myData <- lapply(res$keys, function(x) .newH2OObject("H2ORawData", conn=conn, key=x, linkToGC=TRUE))
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
h2o.importFile <- function(path, conn = h2o.getConnection(), key = "", parse = TRUE, header, sep = "", col.names) {
  h2o.importFolder(path, conn, pattern = "", key, parse, header, sep, col.names)
}

#'
#' Import A URL
#'
#' Import a data source from a URL.
h2o.importURL <- function(path, conn = h2o.getConnection(), key = "", parse = TRUE, header, sep = "", col.names) {
  .Deprecated("h2o.importFolder")
  h2o.importFile(path, conn, key, parse, header, sep, col.names)
}

#'
#' Import HDFS
#'
#' Import from an HDFS location.
h2o.importHDFS <- function(path, conn = h2o.getConnection(), pattern = "", key = "", parse = TRUE, header, sep = "", col.names) {
  .Deprecated("h2o.importFolder")
  h2o.importFolder(path, conn, pattern, key, parse, header, sep, col.names)
}

#'
#' Upload Data
#'
#' Upload local files to the H2O instance.
h2o.uploadFile <- function(path, conn = h2o.getConnection(), key = "", parse = TRUE, header, sep = "", col.names) {
  if (is(path, "H2OConnection")) {
    temp <- path
    path <- conn
    conn <- temp
  }
  if(!is(conn, "H2OConnection")) stop("`conn` must be of class H2OConnection")
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  .key.validate(key)
  if(!is.logical(parse) || length(parse) != 1L || is.na(parse))
    stop("`parse` must be TRUE or FALSE")

  path <- normalizePath(path, winslash = "/")
  srcKey <- .key.make(conn, path)
  urlSuffix <- sprintf("PostFile.json?destination_key=%s",  curlEscape(srcKey))
  fileUploadInfo <- fileUpload(path)
  .h2o.doSafePOST(conn = conn, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = urlSuffix,
                  fileUploadInfo = fileUploadInfo)

  rawData <- .newH2OObject("H2ORawData", conn=conn, key=srcKey, linkToGC=TRUE)
  if (parse) {
    h2o.parseRaw(data=rawData, key=key, header=header, sep=sep, col.names=col.names)
  } else {
    rawData
  }
}

#'
#' Load H2O Model from HDFS or Local Disk
#'
#' Load a saved H2O model from disk.
h2o.loadModel <- function(path, conn = h2o.getConnection()) {
  if (is(path, "H2OConnection")) {
    temp <- path
    path <- conn
    conn <- temp
  }
  if(!is(conn, 'H2OConnection')) stop('`conn` must be of class H2OConnection')
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  res <- .h2o.__remoteSend(conn, .h2o.__PAGE_LoadModel, path = path)
  h2o.getModel(res$model$'_key', conn)
}
