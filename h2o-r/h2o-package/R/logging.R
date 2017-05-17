#' Starting H2O For examples
#'
#' @name aaa
#' @examples
#'if(Sys.info()['sysname'] == "Darwin" && Sys.info()['release'] == '13.4.0'){
#'  quit(save="no")
#'}else{
#'  h2o.init(nthreads = 2)
#'}
NULL

#' Shutdown H2O cloud after examples run
#'
#' @name zzz
#' @examples
#' library(h2o)
#' h2o.init()
#' h2o.shutdown(prompt = FALSE)
#' Sys.sleep(3)
NULL

# Initialize functions for R logging
.h2o.calcLogFileName <- function() {
  paste0(tempdir(), "/rest.log")
}

.h2o.getLogFileName <- function() {
  name <- get("LOG_FILE_NAME", .pkg.env)
  if (is.null(name))
    name <- .h2o.calcLogFileName()
  name
}

.h2o.isLogging <- function() {
  get("IS_LOGGING", .pkg.env)
}

.h2o.logRest <- function(message) {
  if (.h2o.isLogging())
    write(x = message, file = .h2o.getLogFileName(), append = TRUE)
}

h2o.logIt <- function(m, tmp, commandOrErr, isPost = TRUE) {
  # Legacy.  Do nothing.
}

#' Start Writing H2O R Logs
#'
#' Begin logging H2o R POST commands and error responses to local disk. Used
#' primarily for debuggin purposes.
#'
#' @param file a character string name for the file, automatically generated
#' @seealso \code{\link{h2o.stopLogging}, \link{h2o.clearLog},
#'          \link{h2o.openLog}}
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' h2o.startLogging()
#' ausPath = system.file("extdata", "australia.csv", package="h2o")
#' australia.hex = h2o.importFile(path = ausPath)
#' h2o.stopLogging()
#' }
#' @export
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

#' Stop Writing H2O R Logs
#'
#' Halt logging of H2O R POST commands and error responses to local disk. Used
#' primarily for debugging purposes.
#'
#' @seealso \code{\link{h2o.startLogging}, \link{h2o.clearLog},
#'          \link{h2o.openLog}}
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' h2o.startLogging()
#' ausPath = system.file("extdata", "australia.csv", package="h2o")
#' australia.hex = h2o.importFile(path = ausPath)
#' h2o.stopLogging()
#' }
#' @export
h2o.stopLogging <- function() {
  assign("IS_LOGGING", FALSE, envir = .pkg.env)
  cat("Logging stopped\n")
}

#' Delete All H2O R Logs
#'
#' Clear all H2O R command and error response logs from the local disk. Used
#' primarily for debugging purposes.
#'
#' @seealso \code{\link{h2o.startLogging}, \link{h2o.stopLogging},
#'          \link{h2o.openLog}}
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' h2o.startLogging()
#' ausPath = system.file("extdata", "australia.csv", package="h2o")
#' australia.hex = h2o.importFile(path = ausPath)
#' h2o.stopLogging()
#' h2o.clearLog()
#' }
#' @export
h2o.clearLog <- function() {
  file.remove(.h2o.getLogFileName())
  cat("Removed file ", .h2o.getLogFileName(), "\n")
}

#' View H2O R Logs
#'
#' Open existing logs of H2O R POST commands and error resposnes on local disk.
#' Used primarily for debugging purposes.
#'
#' @param type Currently unimplemented.
#' @seealso \code{\link{h2o.startLogging}, \link{h2o.stopLogging},
#'          \link{h2o.clearLog}}
#' @examples
#' \dontrun{
#' h2o.init()
#'
#' h2o.startLogging()
#' ausPath = system.file("extdata", "australia.csv", package="h2o")
#' australia.hex = h2o.importFile(path = ausPath)
#' h2o.stopLogging()
#'
#' # Not run to avoid windows being opened during R CMD check
#' # h2o.openLog("Command")
#' # h2o.openLog("Error")
#' }
#' @export
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
#' @param message A character string with the message to write to the log.
#' @export
h2o.logAndEcho <- function(message) {
  if(!is.character(message))
    stop("`message` must be a character string")
  res <- .h2o.__remoteSend(.h2o.__LOGANDECHO, message = message, method = "POST")
  res$message
}

#' Download H2O Log Files to Disk
#'
#' \code{h2o.downloadAllLogs} downloads all H2O log files to local disk in .zip format. Generally used for debugging purposes.
#'
#' @param dirname (Optional) A character string indicating the directory that the log file should be saved in.
#' @param filename (Optional) A character string indicating the name that the log file should be saved to. Note that the saved format is .zip, so the file name must include the .zip extension.
#' @examples
#' \donttest{
#' h2o.downloadAllLogs(dirname='./your_directory_name/', filename = 'autoh2o_log.zip')
#' }
#' @export
h2o.downloadAllLogs <- function(dirname = ".", filename = NULL) {
  if(!is.character(dirname) || length(dirname) != 1L || is.na(dirname) || !nzchar(dirname))
    stop("`dirname` must be a non-empty character string")

  if(!is.character(filename) || length(filename) != 1L || is.na(filename) || !nzchar(filename))
    stop("`filename` must be a non-empty character string")

  conn <- h2o.getConnection()
  url <- paste0("http://", conn@ip, ":", conn@port, "/", .h2o.__DOWNLOAD_LOGS)
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
