#'
#' H2O <-> R Communication and Utility Methods
#'
#' Here are collected various methods used by the h2o R package to communicate with the H2O
#' backend. There are methods for checking cluster health, polling, and inspecting objects in
#' the H2O store.

#-----------------------------------------------------------------------------------------------------------------------
#   GET & POST
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Make an HTTP request to the H2O backend.
.h2o.__remoteSend <- function(client, page, ...) {
  .h2o.__checkClientHealth(client)
  ip <- client@ip
  port <- client@port
  myURL <- paste("http://", ip, ":", port, "/", page, sep="")

  # Sends the given arguments as URL arguments to the given page on the specified server
  #
  # Re-enable POST since we found the bug in NanoHTTPD which was causing POST
  # payloads to be dropped.
  #
  if(.pkg.env$IS_LOGGING) {
    # Log list of parameters sent to H2O
    .h2o.__logIt(myURL, list(...), "Command")

    hg <- basicHeaderGatherer()
    tg <- basicTextGatherer()
    postForm(myURL, style = "POST", .opts = curlOptions(headerfunction = hg$update, writefunc = tg[[1]]), ...)
    temp <- tg$value()

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


#-----------------------------------------------------------------------------------------------------------------------
#   H2O Server Health
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Check H2O Server Health
#'
#' Warn if there are sick nodes.
.h2o.__checkClientHealth <- function(client) {
  grabCloudStatus <- function(client) {
    ip <- client@ip
    port <- client@port
    url <- paste("http://", ip, ":", port, "/", .h2o.__CLOUD, sep = "")
    if(!.uri.exists(url)) stop(paste("H2O connection has been severed. Instance no longer up at address ", ip, ":", port, "/", sep = "", collapse = ""))
    fromJSON(getURLContent(url))
  }
  checker <- function(node, client) {
    status <- as.logical(node$healthy)
    elapsed <- as.integer(as.POSIXct(Sys.time()))*1000 - node$last_ping
    nport <- unlist(strsplit(node$h2o$node, ":"))[2]
    print(elapsed)
    if(!status) .h2o.__cloudSick(node_name = NULL, client = client)
    if(elapsed > 60000) .h2o.__cloudSick(node_name = NULL, client = client)
    if(elapsed > 10000) {
        Sys.sleep(5)
        lapply(grabCloudStatus(client)$nodes, checker, client)
    }
    return(0)
  }
  cloudStatus <- grabCloudStatus(client)
  if(cloudStatus$bad_nodes != 0) .h2o.__cloudSick(node_name = NULL, client = client)
  lapply(cloudStatus$nodes, checker, client)
  return(0)
}

#'
#' Helper method to issue a warning.
.h2o.__cloudSick <- function(node_name = NULL, client) {
  url <- paste("http://", client@ip, ":", client@port, "/Cloud.html", sep = "")
  m1 <- "Attempting to execute action on an unhealthy cluster!\n"
  m2 <- ifelse(node_name != NULL, paste("The sick node is identified to be: ", node_name, "\n", sep = "", collapse = ""), "")
  m3 <- paste("Check cloud status here: ", url, sep = "", collapse = "")
  m <- paste(m1, m2, "\n", m3, sep = "")
  warning(m)
}


#-----------------------------------------------------------------------------------------------------------------------
#   Job Polling
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Job Polling Top-Level Function
#'
#' Poll the H2O server with the current job key `job_key` for completion.
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

#'
#' Return the progress so far.
.h2o.__poll <- function(client, keyName) {
  if(missing(client)) stop("client is missing!")
  if(class(client) != "H2OClient") stop("client must be a H2OClient object")
  if(missing(keyName)) stop("keyName is missing!")
  if(!is.character(keyName) || nchar(keyName) == 0) stop("keyName must be a non-empty string")

  res = .h2o.__remoteSend(client, .h2o.__JOBS)
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
  if (prog$progress < 0 && (prog$end_time == "" || is.null(prog$end_time))) return(abs(prog$progress)/100)
  else return(prog$progress)
}

#'
#' Cancel a job.
.h2o.__cancelJob <- function(client, keyName) {
  res = .h2o.__remoteSend(client, .h2o.__JOBS)
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

#'
#' Check if any jobs are still running.
.h2o.__allDone <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__JOBS)
  notDone = lapply(res$jobs, function(x) { !(x$progress == -1.0 || x$cancelled) })
  !any(unlist(notDone))
}

#'
#' Poll on all jobs until they are all done.
.h2o.__pollAll <- function(client, timeout) {
  start = Sys.time()
  while(!.h2o.__allDone(client)) {
    Sys.sleep(1)
    if(as.numeric(difftime(Sys.time(), start)) > timeout)
      stop("Timeout reached! Check if any jobs have frozen in H2O.")
  }
}

#------------------------------------ Exec2 ------------------------------------#

#
#.h2o.__castType <- function(object) {
#  if(!inherits(object, "H2OParsedData")) stop("object must be a H2OParsedData or H2OParsedDataVA object")
#  .h2o.__checkClientHealth(object@h2o)
#  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_INSPECT, key = object@key)
#  if(is.null(res$value_size_bytes))
#    return(new("H2OParsedData", h2o=object@h2o, key=object@key))
#  else
#    return(new("H2OParsedDataVA", h2o=object@h2o, key=object@key))
#}

#------------------------------------ Utilities ------------------------------------#

.h2o.__checkForFactors <- function(object) {
  if(class(object) != "H2OParsedData") return(FALSE)
  h2o.anyFactor(object)
}

.h2o.__version <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__CLOUD)
  res$version
}

.readableTime <- function(epochTimeMillis) {
  days = epochTimeMillis/(24*60*60*1000)
  hours = (days-trunc(days))*24
  minutes = (hours-trunc(hours))*60
  seconds = (minutes-trunc(minutes))*60
  milliseconds = (seconds-trunc(seconds))*1000
  durationVector = trunc(c(days,hours,minutes,seconds,milliseconds))
  names(durationVector) = c("days","hours","minutes","seconds","milliseconds")
  if(length(durationVector[durationVector > 0]) > 1)
    showVec <- durationVector[durationVector > 0][1:2]
  else
    showVec <- durationVector[durationVector > 0]
  x1 = as.numeric(showVec)
  x2 = names(showVec)
  return(paste(x1,x2))
}

.uri.exists <- function(uri) {
  h <- basicHeaderGatherer()
  invisible(getURI(uri, headerfunction = h$update))
  "200" == as.list(h$value())$status
}

h2o.clusterInfo <- function(client) {
  if(missing(client) || class(client) != "H2OClient") stop("client must be a H2OClient object")
  myURL <- paste("http://", client@ip, ":", client@port, "/", .h2o.__CLOUD, sep = "")
  if(!.uri.exists(myURL)) stop("Cannot connect to H2O instance at ", myURL)

  res = NULL
  {
    res <- fromJSON(postForm(myURL, style = "POST"))

    nodeInfo <- res$nodes
    numCPU <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

    if (numCPU == 0) {
      # If the cloud has not been up for a few seconds yet, then query again.
      # Sometimes the heartbeat info with cores and memory has not had a chance
      # to post its information yet.
      threeSeconds = 3
      Sys.sleep(threeSeconds)
      res <- fromJSON(postForm(myURL, style = "POST"))
    }
  }

  nodeInfo <- res$nodes
  maxMem   <- sum(sapply(nodeInfo,function(x) as.numeric(x['max_mem']))) / (1024 * 1024 * 1024)
  numCPU   <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))
  clusterHealth <- all(sapply(nodeInfo,function(x) as.logical(x['healthy']))==TRUE)

  cat("R is connected to H2O cluster:\n")
  cat("    H2O cluster uptime:       ", .readableTime(as.numeric(res$cloud_uptime_millis)), "\n")
  cat("    H2O cluster version:      ", res$version, "\n")
  cat("    H2O cluster name:         ", res$cloud_name, "\n")
  cat("    H2O cluster total nodes:  ", res$cloud_size, "\n")
  cat("    H2O cluster total memory: ", sprintf("%.2f GB", maxMem), "\n")
  cat("    H2O cluster total cores:  ", numCPU, "\n")
  cat("    H2O cluster healthy:      ", clusterHealth, "\n")
}

.h2o.__formatError <- function(error,prefix="  ") {
  result = ""
  items = strsplit(as.character(error),"\n")[[1]];
  for (i in 1:length(items))
    result = paste(result,prefix,items[i],"\n",sep="")
  result
}
