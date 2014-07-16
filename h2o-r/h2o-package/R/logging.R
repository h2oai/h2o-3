# Initialize functions for R logging
.myPath = paste(Sys.getenv("HOME"), "Library", "Application Support", "h2o", sep=.Platform$file.sep)
if(.Platform$OS.type == "windows")
  .myPath = paste(Sys.getenv("APPDATA"), "h2o", sep=.Platform$file.sep)

.pkg.env$h2o.__LOG_COMMAND = paste(.myPath, "commands.log", sep=.Platform$file.sep)
.pkg.env$h2o.__LOG_ERROR = paste(.myPath, "errors.log", sep=.Platform$file.sep)

h2o.startLogging     <- function() {
  cmdDir <- normalizePath(dirname(.pkg.env$h2o.__LOG_COMMAND))
  errDir <- normalizePath(dirname(.pkg.env$h2o.__LOG_ERROR))
  if(!file.exists(cmdDir)) stop(cmdDir, " directory does not exist. Please create it or change logging path with h2o.setLogPath")
  if(!file.exists(errDir)) stop(errDir, " directory does not exist. Please create it or change logging path with h2o.setLogPath")

  cat("Appending to log file", .pkg.env$h2o.__LOG_COMMAND, "\n")
  cat("Appending to log file", .pkg.env$h2o.__LOG_ERROR, "\n")
  assign("IS_LOGGING", TRUE, envir = .pkg.env)
}
h2o.stopLogging      <- function() { cat("Logging stopped"); assign("IS_LOGGING", FALSE, envir = .pkg.env) }
h2o.clearLogs        <- function() { file.remove(.pkg.env$h2o.__LOG_COMMAND)
                                     file.remove(.pkg.env$h2o.__LOG_ERROR) }
h2o.getLogPath <- function(type) {
  if(missing(type) || !type %in% c("Command", "Error"))
    stop("type must be either 'Command' or 'Error'")
  switch(type, Command = .pkg.env$h2o.__LOG_COMMAND, Error = .pkg.env$h2o.__LOG_ERROR)
}

h2o.openLog <- function(type) {
  if(missing(type) || !type %in% c("Command", "Error"))
    stop("type must be either 'Command' or 'Error'")
  myFile = switch(type, Command = .pkg.env$h2o.__LOG_COMMAND, Error = .pkg.env$h2o.__LOG_ERROR)
  if(!file.exists(myFile)) stop(myFile, " does not exist")

  myOS = Sys.info()["sysname"]
  if(myOS == "Windows") shell.exec(paste("open '", myFile, "'", sep=""))
  else system(paste("open '", myFile, "'", sep=""))
}

h2o.setLogPath <- function(path, type) {
  if(missing(path) || !is.character(path)) stop("path must be a character string")
  if(!file.exists(path)) stop(path, " directory does not exist")
  if(missing(type) || !type %in% c("Command", "Error"))
    stop("type must be either 'Command' or 'Error'")

  myVar = switch(type, Command = "h2o.__LOG_COMMAND", Error = "h2o.__LOG_ERROR")
  myFile = switch(type, Command = "commands.log", Error = "errors.log")
  cmd <- paste(path, myFile, sep = .Platform$file.sep)
  assign(myVar, cmd, envir = .pkg.env)
}

.h2o.__logIt <- function(m, tmp, commandOrErr, isPost = TRUE) {
  # m is a url if commandOrErr == "Command"
  if(is.null(tmp) || is.null(get("tmp"))) s <- m
  else {
    tmp <- get("tmp"); nams = names(tmp)
    if(length(nams) != length(tmp)) {
        if (is.null(nams) && commandOrErr != "Command") nams = "[WARN/ERROR]"
    }
    s <- rep(" ", max(length(tmp), length(nams)))
    for(i in seq_along(tmp)){
      s[i] <- paste(nams[i], ": ", tmp[[i]], sep="", collapse = " ")
    }
    s <- paste(m, "\n", paste(s, collapse = ", "), ifelse(nchar(s) > 0, "\n", ""))
  }
  # if(commandOrErr != "Command") s <- paste(s, '\n')
  h <- format(Sys.time(), format = "%a %b %d %X %Y %Z", tz = "GMT")
  if(commandOrErr == "Command")
    h <- paste(h, ifelse(isPost, "POST", "GET"), sep = "\n")
  s <- paste(h, "\n", s)

  myFile <- ifelse(commandOrErr == "Command", .pkg.env$h2o.__LOG_COMMAND, .pkg.env$h2o.__LOG_ERROR)
  myDir <- normalizePath(dirname(myFile))
  if(!file.exists(myDir)) stop(myDir, " directory does not exist")
  write(s, file = myFile, append = TRUE)
}


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
