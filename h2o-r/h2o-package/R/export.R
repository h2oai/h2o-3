#'
#' Data Export
#'
#' Export data to local disk or HDFS.
#' Save models to local disk or HDFS.

h2o.exportFile <- function(data, path, force = FALSE) {
  if (!is(data, "H2OParsedData"))
    stop("data must be an H2OParsedData object")

  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("path must be a non-empty character string")

  if(!is.logical(force) || length(force) != 1L || is.na(force))
    stop("force must be TRUE or FALSE")
  force <- as.integer(force)

  .h2o.__remoteSend(data@h2o, .h2o.__PAGE_EXPORTFILES, src_key = data@key, path = path, force = force)
}

h2o.exportHDFS <- function(object, path) {
  if(!is(object, "H2OModel"))
    stop("object must be an H2OModel object")

  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("path must be a non-empty character string")

  .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_EXPORTHDFS, source_key = object@key, path = path)
}

h2o.downloadCSV <- function(data, filename) {
  if (!is(data, "H2OParsedData"))
    stop("data must be an H2OParsedData object")

  str <- paste0('http://', data@h2o@ip, ':', data@h2o@port, '/2/DownloadDataset?src_key=', data@key)
  has_wget <- nzchar(Sys.which('wget'))
  has_curl <- nzchar(Sys.which('curl'))
  if(!(has_wget || has_curl))
    stop("could not find wget or curl in system environment")

  if(has_wget){
    cmd <- "wget"
    args <- paste("-O", filename, str)
  } else {
    cmd <- "curl"
    args <- paste("-o", filename, str)
  }
  cat("cmd:", cmd, "\n")
  cat("args:", args, "\n")
  val <- system2(cmd, args, wait = TRUE)
  if(val != 0L)
    cat("Bad return val", val, "\n")
}

# ------------------- Save H2O Model to Disk ----------------------------------------------------
h2o.saveModel <- function(object, dir="", name="", filename="", force=FALSE) {
  if(!is(object, "H2OModel"))
    stop("object must be an H2OModel object")

  if(!is.character(dir) || length(dir) != 1L || is.na(dir))
    stop("dir must be a character string")

  if(!is.character(name) || length(name) != 1L || is.na(name))
    stop("name must be a character string")
  else if(!nzchar(name))
    name <- object@key

  if(!is.character(filename) || length(filename) != 1L || is.na(filename))
    stop("filename must be a character string")

  if(!is.logical(force) || length(force) != 1L || is.na(force))
    stop("force must be TRUE or FALSE")
  force <- as.integer(force)

  if(nzchar(filename))
    path <- filename
  else
    path <- file.path(dir, name)

  res <- .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_SaveModel, model=object@key, path=path, force=force)

  path
}
