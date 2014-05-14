# Hack to get around Exec.json always dumping to same Result.hex key
# TODO: Need better way to manage temporary/intermediate values in calculations! Right now, overwriting occurs silently
.pkg.env = new.env()
.pkg.env$result_count = 0
.pkg.env$temp_count = 0
.pkg.env$IS_LOGGING = FALSE

.TEMP_KEY = "Last.value"
.RESULT_MAX = 1000
.MAX_INSPECT_ROW_VIEW = 10000
.MAX_INSPECT_COL_VIEW = 10000
.LOGICAL_OPERATORS = c("==", ">", "<", "!=", ">=", "<=", "&", "|", "&&", "||", "!", "is.na")

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

# Internal functions & declarations
.h2o.__PAGE_CANCEL = "Cancel.json"
.h2o.__PAGE_CLOUD = "Cloud.json"
.h2o.__PAGE_GET = "GetVector.json"
.h2o.__PAGE_IMPORTURL = "ImportUrl.json"
.h2o.__PAGE_IMPORTFILES = "ImportFiles.json"
.h2o.__PAGE_IMPORTHDFS = "ImportHdfs.json"
.h2o.__PAGE_EXPORTHDFS = "ExportHdfs.json"
.h2o.__PAGE_INSPECT = "Inspect.json"
.h2o.__PAGE_JOBS = "Jobs.json"
.h2o.__PAGE_PARSE = "Parse.json"
.h2o.__PAGE_PREDICT = "GeneratePredictionsPage.json"
.h2o.__PAGE_PUT = "PutVector.json"
.h2o.__PAGE_REMOVE = "Remove.json"
.h2o.__PAGE_REMOVEALL = "2/RemoveAll.json"
.h2o.__PAGE_SUMMARY = "SummaryPage.json"
.h2o.__PAGE_SHUTDOWN = "Shutdown.json"
.h2o.__PAGE_VIEWALL = "StoreView.json"
.h2o.__DOWNLOAD_LOGS = "LogDownload.json"

.h2o.__PAGE_GLM = "GLM.json"
.h2o.__PAGE_GLMProgress = "GLMProgressPage.json"
.h2o.__PAGE_GLMGrid = "GLMGrid.json"
.h2o.__PAGE_GLMGridProgress = "GLMGridProgress.json"
.h2o.__PAGE_KMEANS = "KMeans.json"
.h2o.__PAGE_KMAPPLY = "KMeansApply.json"
.h2o.__PAGE_KMSCORE = "KMeansScore.json"
.h2o.__PAGE_RF  = "RF.json"
.h2o.__PAGE_RFVIEW = "RFView.json"
.h2o.__PAGE_RFTREEVIEW = "RFTreeView.json"

.h2o.__PAGE_EXEC2 = "2/Exec2.json"
.h2o.__PAGE_IMPORTFILES2 = "2/ImportFiles2.json"
.h2o.__PAGE_EXPORTFILES = "2/ExportFiles.json"
.h2o.__PAGE_INSPECT2 = "2/Inspect2.json"
.h2o.__PAGE_PARSE2 = "2/Parse2.json"
.h2o.__PAGE_PREDICT2 = "2/Predict.json"
.h2o.__PAGE_SUMMARY2 = "2/SummaryPage2.json"
.h2o.__PAGE_LOG_AND_ECHO = "2/LogAndEcho.json"
.h2o.__HACK_LEVELS = "Levels.json"
.h2o.__HACK_LEVELS2 = "2/Levels2.json"
.h2o.__HACK_SETCOLNAMES = "SetColumnNames.json"
.h2o.__HACK_SETCOLNAMES2 = "2/SetColumnNames2.json"
.h2o.__PAGE_CONFUSION = "2/ConfusionMatrix.json"
.h2o.__PAGE_AUC = "2/AUC.json"
.h2o.__PAGE_HITRATIO = "2/HitRatio.json"
.h2o.__PAGE_GAPSTAT = "2/GapStatistic.json"
.h2o.__PAGE_GAPSTATVIEW = "2/GapStatisticModelView.json"
.h2o.__PAGE_QUANTILES = "2/QuantilesPage.json"

.h2o.__PAGE_DRF = "2/DRF.json"
.h2o.__PAGE_DRFProgress = "2/DRFProgressPage.json"
.h2o.__PAGE_DRFModelView = "2/DRFModelView.json"
.h2o.__PAGE_GBM = "2/GBM.json"
.h2o.__PAGE_GBMProgress = "2/GBMProgressPage.json"
.h2o.__PAGE_GRIDSEARCH = "2/GridSearchProgress.json"
.h2o.__PAGE_GBMModelView = "2/GBMModelView.json"
.h2o.__PAGE_GLM2 = "2/GLM2.json"
.h2o.__PAGE_GLM2Progress = "2/GLMProgress.json"
.h2o.__PAGE_GLMModelView = "2/GLMModelView.json"
.h2o.__PAGE_GLMValidView = "2/GLMValidationView.json"
.h2o.__PAGE_GLM2GridView = "2/GLMGridView.json"
.h2o.__PAGE_KMEANS2 = "2/KMeans2.json"
.h2o.__PAGE_KM2Progress = "2/KMeans2Progress.json"
.h2o.__PAGE_KM2ModelView = "2/KMeans2ModelView.json"
.h2o.__PAGE_DeepLearning = "2/DeepLearning.json"
.h2o.__PAGE_DeepLearningProgress = "2/DeepLearningProgressPage.json"
.h2o.__PAGE_DeepLearningModelView = "2/DeepLearningModelView.json"
.h2o.__PAGE_PCA = "2/PCA.json"
.h2o.__PAGE_PCASCORE = "2/PCAScore.json"
.h2o.__PAGE_PCAProgress = "2/PCAProgressPage.json"
.h2o.__PAGE_PCAModelView = "2/PCAModelView.json"
.h2o.__PAGE_SpeeDRF = "2/SpeeDRF.json"
.h2o.__PAGE_SpeeDRFModelView = "2/SpeeDRFModelView.json"
.h2o.__PAGE_BAYES = "2/NaiveBayes.json"
.h2o.__PAGE_NBProgress = "2/NBProgressPage.json"
.h2o.__PAGE_NBModelView = "2/NBModelView.json"

# client -- Connection object returned from h2o.init().
# page   -- URL to access within the H2O server.
# parms  -- List of parameters to send to the server.
.h2o.__remoteSendWithParms <- function(client, page, parms) {
  cmd = ".h2o.__remoteSend(client, page"

  for (i in 1:length(parms)) {
    thisparmname = names(parms)[i]
    cmd = sprintf("%s, %s=parms$%s", cmd, thisparmname, thisparmname)
  }

  cmd = sprintf("%s)", cmd)
  #cat(sprintf("TOM: cmd is %s\n", cmd))
  
  rv = eval(parse(text=cmd))
  return(rv)
}

.h2o.__remoteSend <- function(client, page, ...) {
  .h2o.__checkClientHealth(client)
  ip = client@ip
  port = client@port
  myURL = paste("http://", ip, ":", port, "/", page, sep="")

  # Sends the given arguments as URL arguments to the given page on the specified server
  #
  # Re-enable POST since we found the bug in NanoHTTPD which was causing POST
  # payloads to be dropped.
  #
  if(.pkg.env$IS_LOGGING) {
    # Log list of parameters sent to H2O
    .h2o.__logIt(myURL, list(...), "Command")
    
    hg = basicHeaderGatherer()
    tg = basicTextGatherer()
    postForm(myURL, style = "POST", .opts = curlOptions(headerfunction = hg$update, writefunc = tg[[1]]), ...)
    temp = tg$value()
    
    # Log HTTP response from H2O
    hh <- hg$value()
    s <- paste(hh["Date"], "\nHTTP status code: ", hh["status"], "\n ", temp, sep = "")
    s <- paste(s, "\n\n------------------------------------------------------------------\n")
    
    cmdDir <- normalizePath(dirname(.pkg.env$h2o.__LOG_COMMAND))
    if(!file.exists(cmdDir)) stop(cmdDir, " directory does not exist")
    write(s, file = .pkg.env$h2o.__LOG_COMMAND, append = TRUE)
  } else
    temp = postForm(myURL, style = "POST", ...)
  
  # The GET code that we used temporarily while NanoHTTPD POST was known to be busted.
  #
  #if(length(list(...)) == 0)
  #  temp = getURLContent(myURL)
  #else
  #  temp = getForm(myURL, ..., .checkParams = FALSE)   # Some H2O params overlap with Curl params
  
  # after = gsub("\\\\\\\"NaN\\\\\\\"", "NaN", temp[1]) 
  # after = gsub("NaN", '"NaN"', after)
  after = gsub('"Infinity"', '"Inf"', temp[1])
  after = gsub('"-Infinity"', '"-Inf"', after)
  res = fromJSON(after)

  if(!is.null(res$error)) {
    if(.pkg.env$IS_LOGGING) .h2o.__writeToFile(res, .pkg.env$h2o.__LOG_ERROR)
    stop(paste(myURL," returned the following error:\n", .h2o.__formatError(res$error)))
  }
  res
}

.h2o.__cloudSick <- function(node_name = NULL, client) {
  url <- paste("http://", client@ip, ":", client@port, "/Cloud.html", sep = "")
  m1 <- "Attempting to execute action on an unhealthy cluster!\n"
  m2 <- ifelse(node_name != NULL, paste("The sick node is identified to be: ", node_name, "\n", sep = "", collapse = ""), "")
  m3 <- paste("Check cloud status here: ", url, sep = "", collapse = "")
  m <- paste(m1, m2, "\n", m3, sep = "")
  stop(m)
}

.h2o.__checkClientHealth <- function(client) {
  grabCloudStatus <- function(client) {
    ip <- client@ip
    port <- client@port
    url <- paste("http://", ip, ":", port, "/", .h2o.__PAGE_CLOUD, sep = "")
    if(!url.exists(url)) stop(paste("H2O connection has been severed. Instance no longer up at address ", ip, ":", port, "/", sep = "", collapse = ""))
    fromJSON(getURLContent(url))
  }
  checker <- function(node, client) {
    status <- node$node_healthy
    elapsed <- node$elapsed_time
    nport <- unlist(strsplit(node$name, ":"))[2]
    if(!status) .h2o.__cloudSick(node_name = node$name, client = client)
    if(elapsed > 45000) .h2o.__cloudSick(node_name = NULL, client = client)
    if(elapsed > 10000) {
        Sys.sleep(5)
        lapply(grabCloudStatus(client)$nodes, checker, client)
    }
    return(0)
  }
  cloudStatus <- grabCloudStatus(client)
  if(!cloudStatus$cloud_healthy) .h2o.__cloudSick(node_name = NULL, client = client)
  lapply(cloudStatus$nodes, checker, client)
  return(0)
}

#------------------------------------ Job Polling ------------------------------------#
.h2o.__poll <- function(client, keyName) {
  if(missing(client)) stop("client is missing!")
  if(class(client) != "H2OClient") stop("client must be a H2OClient object")
  if(missing(keyName)) stop("keyName is missing!")
  if(!is.character(keyName) || nchar(keyName) == 0) stop("keyName must be a non-empty string")
  
  res = .h2o.__remoteSend(client, .h2o.__PAGE_JOBS)
  res = res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog = NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key == keyName)
      prog = res[[i]]
  }
  if(is.null(prog)) stop("Job key ", keyName, " not found in job queue")
  # if(prog$end_time == -1 || prog$progress == -2.0) stop("Job key ", keyName, " has been cancelled")
  if(!is.null(prog$result$val) && prog$result$val == "CANCELLED") stop("Job key ", keyName, " was cancelled by user")
  else if(!is.null(prog$result$exception) && prog$result$exception == 1) stop(prog$result$val)
  prog$progress
}

.h2o.__allDone <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__PAGE_JOBS)
  notDone = lapply(res$jobs, function(x) { !(x$progress == -1.0 || x$cancelled) })
  !any(unlist(notDone))
}

.h2o.__pollAll <- function(client, timeout) {
  start = Sys.time()
  while(!.h2o.__allDone(client)) {
    Sys.sleep(1)
    if(as.numeric(difftime(Sys.time(), start)) > timeout)
      stop("Timeout reached! Check if any jobs have frozen in H2O.")
  }
}

.h2o.__waitOnJob <- function(client, job_key, pollInterval = 1, progressBar = TRUE) {
  if(!is.character(job_key) || nchar(job_key) == 0) stop("job_key must be a non-empty string")
  if(progressBar) {
    pb = txtProgressBar(style = 3)
    tryCatch(while((prog = .h2o.__poll(client, job_key)) != -1) { Sys.sleep(pollInterval); setTxtProgressBar(pb, prog) },
             error = function(e) { cat("\nPolling fails:\n"); print(e) },
             finally = .h2o.__cancelJob(client, job_key))
    setTxtProgressBar(pb, 1.0); close(pb)
  } else
    tryCatch(while(.h2o.__poll(client, job_key) != -1) { Sys.sleep(pollInterval) }, 
             finally = .h2o.__cancelJob(client, job_key))
}

# For checking progress from each algorithm's progress page (no longer used)
# .h2o.__isDone <- function(client, algo, resH) {
#   if(!algo %in% c("GBM", "KM", "RF1", "RF2", "DeepLearning", "GLM1", "GLM2", "GLM1Grid", "PCA")) stop(algo, " is not a supported algorithm")
#   version = ifelse(algo %in% c("RF1", "GLM1", "GLM1Grid"), 1, 2)
#   page = switch(algo, GBM = .h2o.__PAGE_GBMProgress, KM = .h2o.__PAGE_KM2Progress, RF1 = .h2o.__PAGE_RFVIEW, 
#                 RF2 = .h2o.__PAGE_DRFProgress, DeepLearning = .h2o.__PAGE_DeepLearningProgress, GLM1 = .h2o.__PAGE_GLMProgress, 
#                 GLM1Grid = .h2o.__PAGE_GLMGridProgress, GLM2 = .h2o.__PAGE_GLM2Progress, PCA = .h2o.__PAGE_PCAProgress)
#   
#   if(version == 1) {
#     job_key = resH$response$redirect_request_args$job
#     dest_key = resH$destination_key
#     if(algo == "RF1")
#       res = .h2o.__remoteSend(client, page, model_key = dest_key, data_key = resH$data_key, response_variable = resH$response$redirect_request_args$response_variable)
#     else
#       res = .h2o.__remoteSend(client, page, job = job_key, destination_key = dest_key)
#     if(res$response$status == "error") stop(res$error)
#     res$response$status != "poll"
#   } else {
#     job_key = resH$job_key; dest_key = resH$destination_key
#     res = .h2o.__remoteSend(client, page, job_key = job_key, destination_key = dest_key)
#     if(res$response_info$status == "error") stop(res$error)
#     
#     if(!is.null(res$response_info$redirect_url)) {
#       ind = regexpr("\\?", res$response_info$redirect_url)[1]
#       url = ifelse(ind > 1, substr(res$response_info$redirect_url, 1, ind-1), res$response_info$redirect_url)
#       !(res$response_info$status == "poll" || (res$response_info$status == "redirect" && url == page))
#     } else
#       res$response_info$status == "done"
#   }
# }

.h2o.__cancelJob <- function(client, keyName) {
  res = .h2o.__remoteSend(client, .h2o.__PAGE_JOBS)
  res = res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog = NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key == keyName) {
      prog = res[[i]]; break
    }
  }
  if(is.null(prog)) stop("Job key ", keyName, " not found in job queue")
  if(!(prog$cancelled || prog$progress == -1.0 || prog$progress == -2.0 || prog$end_time == -1)) {
    .h2o.__remoteSend(client, .h2o.__PAGE_CANCEL, key=keyName)
    cat("Job key", keyName, "was cancelled by user\n")
  }
}

#------------------------------------ Exec2 ------------------------------------#
.h2o.__exec2 <- function(client, expr) {
  destKey = paste(.TEMP_KEY, ".", .pkg.env$temp_count, sep="")
  .pkg.env$temp_count = (.pkg.env$temp_count + 1) %% .RESULT_MAX
  .h2o.__exec2_dest_key(client, expr, destKey)
  # .h2o.__exec2_dest_key(client, expr, .TEMP_KEY)
}

.h2o.__exec2_dest_key <- function(client, expr, destKey) {
  type = tryCatch({ typeof(expr) }, error = function(e) { "expr" })
  if (type != "character")
    expr = deparse(substitute(expr))
  expr = paste(destKey, "=", expr)
  res = .h2o.__remoteSend(client, .h2o.__PAGE_EXEC2, str=expr)
  if(!is.null(res$response$status) && res$response$status == "error") stop("H2O returned an error!")
  res$dest_key = destKey
  return(res)
}

.h2o.__unop2 <- function(op, x) {
  if(missing(x)) stop("Must specify data set")
  if(!(class(x) %in% c("H2OParsedData","H2OParsedDataVA"))) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))
  
  expr = paste(op, "(", x@key, ")", sep = "")
  res = .h2o.__exec2(x@h2o, expr)
  if(res$num_rows == 0 && res$num_cols == 0)
    return(ifelse(op %in% .LOGICAL_OPERATORS, as.logical(res$scalar), res$scalar))
  if(op %in% .LOGICAL_OPERATORS)
    new("H2OParsedData", h2o=x@h2o, key=res$dest_key, logic=TRUE)
  else
    new("H2OParsedData", h2o=x@h2o, key=res$dest_key, logic=FALSE)
}

.h2o.__binop2 <- function(op, x, y) {
  # if(!((ncol(x) == 1 || class(x) == "numeric") && (ncol(y) == 1 || class(y) == "numeric")))
  #  stop("Can only operate on single column vectors")
  # LHS = ifelse(class(x) == "H2OParsedData", x@key, x)
  LHS = ifelse(inherits(x, "H2OParsedData"), x@key, x)
  
  # if((class(x) == "H2OParsedData" || class(y) == "H2OParsedData") & !( op %in% c('==', '!='))) {
  if((inherits(x, "H2OParsedData") || inherits(y, "H2OParsedData")) & !( op %in% c('==', '!='))) {
    anyFactorsX <- .h2o.__checkForFactors(x)
    anyFactorsY <- .h2o.__checkForFactors(y)
    anyFactors <- any(c(anyFactorsX, anyFactorsY))
    if(anyFactors) warning("Operation not meaningful for factors.")
  }
  
  # RHS = ifelse(class(y) == "H2OParsedData", y@key, y)
  RHS = ifelse(inherits(y, "H2OParsedData"), y@key, y)
  expr = paste(LHS, op, RHS)
  # if(class(x) == "H2OParsedData") myClient = x@h2o
  if(inherits(x, "H2OParsedData")) myClient = x@h2o
  else myClient = y@h2o
  res = .h2o.__exec2(myClient, expr)
  
  if(res$num_rows == 0 && res$num_cols == 0)
    return(ifelse(op %in% .LOGICAL_OPERATORS, as.logical(res$scalar), res$scalar))
  if(op %in% .LOGICAL_OPERATORS)
    new("H2OParsedData", h2o=myClient, key=res$dest_key, logic=TRUE)
  else
    new("H2OParsedData", h2o=myClient, key=res$dest_key, logic=FALSE)
}

.h2o.__castType <- function(object) {
  if(!inherits(object, "H2OParsedData")) stop("object must be a H2OParsedData or H2OParsedDataVA object")
  .h2o.__checkClientHealth(object@h2o)
  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_INSPECT, key = object@key)
  if(is.null(res$value_size_bytes))
    return(new("H2OParsedData", h2o=object@h2o, key=object@key))
  else
    return(new("H2OParsedDataVA", h2o=object@h2o, key=object@key))
}

#------------------------------------ Utilities ------------------------------------#
.h2o.__writeToFile <- function(res, fileName) {
  formatVector = function(vec) {
    result = rep(" ", length(vec))
    nams = names(vec)
    for(i in 1:length(vec))
      result[i] = paste(nams[i], ": ", vec[i], sep="")
    paste(result, collapse="\n")
  }
  
  cat("Writing JSON response to", fileName, "\n")
  temp = strsplit(as.character(Sys.time()), " ")[[1]]
  # myDate = gsub("-", "", temp[1]); myTime = gsub(":", "", temp[2])
  write(paste(temp[1], temp[2], '\t', formatVector(unlist(res))), file = fileName, append = TRUE)
  # writeLines(unlist(lapply(res$response, paste, collapse=" ")), fileConn)
}

.h2o.__formatError <- function(error,prefix="  ") {
  result = ""
  items = strsplit(error,"\n")[[1]];
  for (i in 1:length(items))
    result = paste(result,prefix,items[i],"\n",sep="")
  result
}

.h2o.__uniqID <- function(prefix = "") {
  hex_digits <- c(as.character(0:9), letters[1:6])
  y_digits <- hex_digits[9:12]
  temp = paste(
    paste(sample(hex_digits, 8, replace=TRUE), collapse='', sep=''),
    paste(sample(hex_digits, 4, replace=TRUE), collapse='', sep=''),
    paste('4', paste(sample(hex_digits, 3, replace=TRUE), collapse='', sep=''), collapse='', sep=''),
    paste(sample(y_digits,1), paste(sample(hex_digits, 3, replace=TRUE), collapse='', sep=''), collapse='', sep = ''),
    paste(sample(hex_digits, 12, replace=TRUE), collapse='', sep=''), sep='-')
  temp = gsub("-", "", temp)
  paste(prefix, temp, sep="_")
}

# Check if key_env$key exists in H2O and remove if it does
# .h2o.__finalizer <- function(key_env) {
#   if("h2o" %in% ls(key_env) && "key" %in% ls(key_env) && class(key_env$h2o) == "H2OClient" && class(key_env$key) == "character" && key_env$key != "") {
#     res = .h2o.__remoteSend(key_env$h2o, .h2o.__PAGE_VIEWALL, filter=key_env$key)
#     if(length(res$keys) != 0)
#       .h2o.__remoteSend(key_env$h2o, .h2o.__PAGE_REMOVE, key=key_env$key)
#   }
# }

.h2o.__checkForFactors <- function(object) {
  # if(class(object) != "H2OParsedData") return(FALSE)
  if(!class(object) %in% c("H2OParsedData", "H2OParsedDataVA")) return(FALSE)
  h2o.anyFactor(object)
}

.h2o.__version <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__PAGE_CLOUD)
  res$version
}

.h2o.__getFamily <- function(family, link, tweedie.var.p = 0, tweedie.link.p = 1-tweedie.var.p) {
  if(family == "tweedie")
    return(tweedie(var.power = tweedie.var.p, link.power = tweedie.link.p))
  
  if(missing(link)) {
    switch(family,
           gaussian = gaussian(),
           binomial = binomial(),
           poisson = poisson(),
           gamma = gamma())
  } else {
    switch(family,
           gaussian = gaussian(link),
           binomial = binomial(link),
           poisson = poisson(link),
           gamma = gamma(link))
  }
}
