# Initialize functions for R logging

.h2o.calcLogFileName <- function() {
  paste0(tempdir(), "/rest.log")
}

.h2o.getLogFileName <- function() {
  if (!is.null(.pkg.env$LOG_FILE_NAME))
    .pkg.env$LOG_FILE_NAME
  else
    .h2o.calcLogFileName()
}

.h2o.isLogging <- function() {
  .pkg.env$IS_LOGGING
}

.h2o.logRest <- function(message) {
  if (.h2o.isLogging())
    write(x = message, file = .h2o.getLogFileName(), append = TRUE)
}

h2o.logIt <- function(m, tmp, commandOrErr, isPost = TRUE) {
  # Legacy.  Do nothing.
}

h2o.startLogging <- function(file) {
  if (missing(file)) {
    logFileName <- .h2o.calcLogFileName()
  } else {
    stopifnot(is.character(file))
    logFileName <- file
  }
  assign("LOG_FILE_NAME", logFileName, .pkg.env)
  assign("IS_LOGGING", TRUE, envir = .pkg.env)
  cat("Appending REST API transactions to log file", logFileName, "\n")
}

h2o.stopLogging <- function() {
  assign("IS_LOGGING", FALSE, envir = .pkg.env)
  cat("Logging stopped\n")
}

h2o.clearLog <- function() {
  file.remove(.h2o.getLogFileName())
  cat("Removed file ", .h2o.getLogFileName(), "\n")
  file.remove(.pkg.env$h2o.__LOG_ERROR)
}

h2o.openLog <- function(type) {
  myFile <- .h2o.getLogFileName()
  if(!file.exists(myFile))
    stop(myFile, " does not exist")

  if(.Platform$OS.type == "windows")
    shell.exec(paste0("open '", myFile, "'"))
  else
    system(paste0("open '", myFile, "'"))
}

#' Log a message on the server-side logs
#' 
#' This is helpful when running several pieces of work one after the other on a single H2O
#' cluster and you want to make a notation in the H2O server side log where one piece of
#' work ends and the next piece of work begins.
#' 
#' \code{h2o.logAndEcho} sends a message to H2O for logging. Generally used for debugging purposes.
#' 
#' @param client An \code{H2OConnection} object pointing to a running H2O cluster.
#' @param message A character string with the message to write to the log.
#' @seealso \code{\link{H2OConnection}}
h2o.logAndEcho <- function(conn, message) {
  if(!is(conn, "H2OConnection"))
    stop("conn must be an H2OConnection")

  if(!is.character(message))
    stop("message must be a character string")

  res <- .h2o.__remoteSend(conn, .h2o.__LOGANDECHO, message = message)
  res$message
}

#' Download H2O Log Files to Disk
#' 
#' \code{h2o.downloadAllLogs} downloads all H2O log files to local disk. Generally used for debugging purposes.
#' 
#' @param client An \code{H2OConnection} object pointing to a running H2O cluster.
#' @param dirname (Optional) A character string indicating the directory that the log file should be saved in.
#' @param filename (Optional) A character string indicating the name that the log file should be saved to.
#' @seealso \code{\link{H2OConnection}}
h2o.downloadAllLogs <- function(client, dirname = ".", filename = NULL) {
  if(!is(client, "H2OConnection"))
    stop("client must be of class H2OConnection")

  if(!is.character(dirname))
    stop("dirname must be of class character")

  if(!is.null(filename)) {
    if(!is.character(filename))
      stop("filename must be of class character")
    else if(!nzchar(filename))
      stop("filename must be a non-empty string")
  }

  url <- paste0("http://", client@ip, ":", client@port, "/", .h2o.__DOWNLOAD_LOGS)
  if(!file.exists(dirname))
    dir.create(dirname)

  cat("Downloading H2O logs from server...\n")
  h <- basicHeaderGatherer()
  tempfile <- getBinaryURL(url, headerfunction = h$update, verbose = TRUE)

  # Get filename from HTTP header of response
  if(is.null(filename)) {
    atch <- h$value()[["Content-Disposition"]]
    ind <- regexpr("filename=", atch)[[1L]]
    if(ind == -1L)
      stop("Header corrupted: Expected attachment filename in Content-Disposition")
    filename <- substr(atch, ind + nchar("filename="), nchar(atch))
  }

  myPath <- file.path(normalizePath(dirname), filename)

  cat("Writing H2O logs to", myPath, "\n")
  writeBin(tempfile, myPath)
}
