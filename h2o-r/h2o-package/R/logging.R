# Initialize functions for R logging
if(.Platform$OS.type == "windows") {
  .myPath <- file.path(Sys.getenv("APPDATA"), "h2o")
} else {
  .myPath <- file.path(Sys.getenv("HOME"), "Library", "Application Support", "h2o")
}

.pkg.env$h2o.__LOG_COMMAND <- file.path(.myPath, "commands.log")
.pkg.env$h2o.__LOG_ERROR   <- file.path(.myPath, "errors.log")

h2o.startLogging <- function() {
  cmdDir <- normalizePath(dirname(.pkg.env$h2o.__LOG_COMMAND))
  if(!file.exists(cmdDir))
    stop(cmdDir, " directory does not exist. Please create it or change logging path with h2o.setLogPath")

  errDir <- normalizePath(dirname(.pkg.env$h2o.__LOG_ERROR))
  if(!file.exists(errDir))
    stop(errDir, " directory does not exist. Please create it or change logging path with h2o.setLogPath")

  cat("Appending to log file", .pkg.env$h2o.__LOG_COMMAND, "\n")
  cat("Appending to log file", .pkg.env$h2o.__LOG_ERROR, "\n")
  assign("IS_LOGGING", TRUE, envir = .pkg.env)
}

h2o.stopLogging <- function() {
  cat("Logging stopped\n")
  assign("IS_LOGGING", FALSE, envir = .pkg.env)
}

h2o.clearLogs <- function() {
  file.remove(.pkg.env$h2o.__LOG_COMMAND)
  file.remove(.pkg.env$h2o.__LOG_ERROR)
}

h2o.getLogPath <- function(type) {
  type <- match.arg(type, c("Command", "Error"))
  switch(type,
         Command = .pkg.env$h2o.__LOG_COMMAND,
         Error   = .pkg.env$h2o.__LOG_ERROR)
}

h2o.openLog <- function(type) {
  myFile <- h2o.getLogPath(type)
  if(!file.exists(myFile))
    stop(myFile, " does not exist")

  if(.Platform$OS.type == "windows")
    shell.exec(paste("open '", myFile, "'", sep=""))
  else
    system(paste("open '", myFile, "'", sep=""))
}

h2o.setLogPath <- function(path, type) {
  if(missing(path) || !is.character(path))
    stop("path must be a character string")
  else if(!file.exists(path))
    stop(path, " directory does not exist")

  type <- match.arg(type, c("Command", "Error"))
  myVar  <- switch(type, Command = "h2o.__LOG_COMMAND", Error = "h2o.__LOG_ERROR")
  myFile <- switch(type, Command = "commands.log",      Error = "errors.log")
  cmd <- file.path(path, myFile)
  assign(myVar, cmd, envir = .pkg.env)
}

.h2o.__logIt <- function(m, tmp, commandOrErr, isPost = TRUE) {
  # m is a url if commandOrErr == "Command"
  if(is.null(tmp) || is.null(get("tmp")))
    s <- m
  else {
    tmp <- get("tmp")
    nams <- names(tmp)
    if(length(tmp) > 0L && is.null(nams) && commandOrErr != "Command")
      nams <- rep.int("[WARN/ERROR]", length(tmp))
    s <- character(max(length(tmp), length(nams)))
    for(i in seq_along(tmp))
      s[i] <- paste0(nams[i], ": ", tmp[[i]], collapse = " ")
    s <- paste(m, "\n", paste(s, collapse = ", "), ifelse(nzchar(s), "\n", ""))
  }
  # if(commandOrErr != "Command") s <- paste(s, '\n')
  h <- format(Sys.time(), format = "%a %b %d %X %Y %Z", tz = "GMT")
  if(commandOrErr == "Command")
    h <- paste(h, ifelse(isPost, "POST", "GET"), sep = "\n")
  s <- paste(h, "\n", s)

  myFile <- ifelse(commandOrErr == "Command", .pkg.env$h2o.__LOG_COMMAND, .pkg.env$h2o.__LOG_ERROR)
  myDir <- normalizePath(dirname(myFile))
  if(!file.exists(myDir))
    stop(myDir, " directory does not exist")
  write(s, file = myFile, append = TRUE)
}

h2o.logAndEcho <- function(conn, message) {
  if(!is(conn, "h2o.client"))
    stop("conn must be an h2o.client")

  if(!is.character(message))
    stop("message must be a character string")

  res <- .h2o.__remoteSend(conn, .h2o.__LOGANDECHO, message = message)
  res$message
}

h2o.downloadAllLogs <- function(client, dirname = ".", filename = NULL) {
  if(!is(client, "h2o.client"))
    stop("client must be of class h2o.client")

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
