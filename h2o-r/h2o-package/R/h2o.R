#'
#' H2O <-> R Communication and Utility Methods
#'
#' Collected here are the various methods used by the h2o-R package to communicate with the H2O
#' backend. There are methods for checking cluster health, polling, and inspecting objects in
#' the H2O store.

#-----------------------------------------------------------------------------------------------------------------------
#   GET & POST
#-----------------------------------------------------------------------------------------------------------------------

.h2o.__REST_API_VERSION = 3

.skip_if_not_developer <- function() {
  if (identical(Sys.getenv("USER"), "tomk")) {
    return()
  }

  skip("Not a developer")
}

.h2o.calcBaseURL <- function(conn, h2oRestApiVersion, urlSuffix) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")

  if (missing(h2oRestApiVersion) || (h2oRestApiVersion < 0)) {
    url = sprintf("http://%s:%s/%s", conn@ip, as.character(conn@port), urlSuffix)
  } else {
    url = sprintf("http://%s:%s/%d/%s", conn@ip, as.character(conn@port), h2oRestApiVersion, urlSuffix)
  }

  return(url)
}

#' Perform a low-level HTTP GET operation on an H2O instance
#'
#' Does not do any I/O level error checking.  Caller must do its own validations.
#' Does not modify the response payload in any way.
#' Log the request and response if h2o.startLogging() has been called.
#'
#' The return value is a list as follows:
#'     $url                -- Final calculated URL.
#'     $curlError          -- TRUE if a socket-level error occurred.  FALSE otherwise.
#'     $curlErrorMessage   -- If curlError is TRUE a message about the error.
#'     $httpStatusCode     -- The HTTP status code.  Usually 200 if the request succeeded.
#'     $httpStatusMessage  -- A string describing the httpStatusCode.
#'     $payload            -- The raw response payload as a character vector.
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, the version prefix is skipped.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
h2o.doRawGET <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")
  if (! missing(parms)) { stopifnot(class(parms) == "list") }
  if (missing(parms)) {
    parms = list()
  }

  url = .h2o.calcBaseURL(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix)

  # Add parameters to the base URL.
  i = 1
  while (i <= length(parms)) {
    name = names(parms)[i]
    value = parms[i]
    escaped_value = curlEscape(value)
    if (i == 1) {
      separator = "?"
    } else {
      separator = "&"
    }
    url = sprintf("%s%s%s=%s", url, separator, name, escaped_value)
    i = i + 1
  }

  .__curlError = FALSE
  .__curlErrorMessage = ""
  httpStatusCode = ""
  httpStatusMessage = ""
  payload = ""

  if (.h2o.isLogging()) {
    .h2o.logRest("------------------------------------------------------------")
    .h2o.logRest("")
    .h2o.logRest(sprintf("GET  %s", url))
  }

  h = basicHeaderGatherer()
  tmp = tryCatch(getURL(url = url, headerfunction = h$update),
                 error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })

  if (! .__curlError) {
    httpStatusCode = as.numeric(h$value()["status"])
    httpStatusMessage = h$value()["statusMessage"]
    payload = tmp
  }

  if (.h2o.isLogging()) {
    .h2o.logRest("")
    .h2o.logRest(paste("curlError:        ", .__curlError))
    .h2o.logRest(paste("curlErrorMessage: ", .__curlErrorMessage))
    .h2o.logRest(paste("httpStatusCode:   ", httpStatusCode))
    .h2o.logRest(paste("httpStatusMessage:", httpStatusMessage))
    .h2o.logRest("")
    .h2o.logRest(payload)
    .h2o.logRest("")
  }

  rv = list(url = url,
            curlError = .__curlError,
            curlErrorMessage = .__curlErrorMessage,
            httpStatusCode = httpStatusCode,
            httpStatusMessage = httpStatusMessage,
            payload = payload)

  return(rv)
}

#' Just like doRawGET but fills in the default h2oRestApiVersion if none is provided
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
h2o.doGET <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")

  if (missing(h2oRestApiVersion)) {
    h2oRestApiVersion = .h2o.__REST_API_VERSION
  }

  rv = h2o.doRawGET(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms)
  return(rv)
}

#' An error-checked version of doGET.
#'
#' This function validates that no CURL error occurred and that the HTTP response code is successful.
#' If a failure occurred, then stop() is called with an error message.
#' Since all necessary error checking is done inside this call, the valid payload is directly returned if the function successfully finishes without calling stop().
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return The raw response payload as a character vector
h2o.doSafeGET <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")

  rv = h2o.doGET(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms)

  if (rv$curlError) {
    stop(sprintf("Unexpected CURL error: %s", rv$curlErrorMessage))
  } else if (rv$httpStatusCode != 200) {
    stop(sprintf("Unexpected HTTP Status code: %d %s (url = %s)", rv$httpStatusCode, rv$httpStatusMessage, rv$url))
  }

  return(rv$payload)
}


#h2o.doRawPOST
#h2o.doPOST
#h2o.doSafePOST

#----------------------------------------


#'
#' Make an HTTP request to the H2O backend.
#'
.h2o.__remoteSend <- function(client, page, method = "GET", ..., .params = list()) {
  .h2o.__checkClientHealth(client)
  ip <- client@ip
  port <- client@port
  myURL <- paste("http://", ip, ":", port, "/", page, sep="")

  # Sends the given arguments as URL arguments to the given page on the specified server
  #
  # Re-enable POST since we found the bug in NanoHTTPD which was causing POST
  # payloads to be dropped.
  #
#  if(.pkg.env$IS_LOGGING) {
    # Log list of parameters sent to H2O
#    .h2o.__logIt(myURL, list(...), "Command")
#
#    hg <- basicHeaderGatherer()
#    tg <- basicTextGatherer()
#    postForm(myURL, style = "POST", .opts = curlOptions(headerfunction = hg$update, writefunc = tg[[1]]), ...)
#    temp <- tg$value()
#
#    # Log HTTP response from H2O
#    hh <- hg$value()
#    s <- paste(hh["Date"], "\nHTTP status code: ", hh["status"], "\n ", temp, sep = "")
#    s <- paste(s, "\n\n------------------------------------------------------------------\n")
#
#    cmdDir <- normalizePath(dirname(.pkg.env$h2o.__LOG_COMMAND))
#    if(!file.exists(cmdDir)) stop(cmdDir, " directory does not exist")
#    write(s, file = .pkg.env$h2o.__LOG_COMMAND, append = TRUE)
#  } else {

    temp <- list()

    if (length(.params) == 0) .params <- list(...)

    # GET
    if (method == "GET") {
      if(length(list(...)) == 0 && length(.params) == 0) {
        #
        # TODO: PUT IN A TRY CATCH AND DUMP EVERYTHING FOR DEBUG
        #
        tryCatch(
        temp <- invisible(getURLContent(myURL))
        , error = function(e) { print("Error!"); print(myURL); print("Keys in H2O:"); print(h2o.ls(client)); print(getURLContent(myURL)); })
        temp <- invisible(getURLContent(myURL))

      } else
        temp = invisible(getForm(myURL, .params = .params, .checkParams = FALSE))  # Some H2O params overlap with Curl params

    # POST
    } else if (method == "POST") {
      temp <- postForm(myURL, .params = .params,  style = "POST")
    } else if (method == "HTTPPOST") {
      hg <- basicHeaderGatherer()
      tg <- basicTextGatherer()
      suppressWarnings(postForm(myURL, .checkParams=FALSE, style = "HTTPPOST", .opts = curlOptions(headerfunction = hg$update, writefunc = tg[[1]]), .params =.params))
      temp <- tg$value()
    }

    # post-processing
    after <- gsub('"Infinity"', '"Inf"', temp[1])
    after <- gsub('"-Infinity"', '"-Inf"', after)
    if (is.null(after)) stop("`after` was NULL !!")
    res <- fromJSON(after)

    if(!is.null(res$error)) {
      if(.pkg.env$IS_LOGGING) .h2o.__writeToFile(res, .pkg.env$h2o.__LOG_ERROR)
      stop(paste(myURL," returned the following error:\n", .h2o.__formatError(res$error)))
    }
  res
}

# client -- Connection object returned from h2o.init().
# page   -- URL to access within the H2O server.
# parms  -- List of parameters to send to the server.
.h2o.__remoteSendWithParms <- function(client, page, method = "GET", parms) {
  cmd = ".h2o.__remoteSend(client, page, method =" %p% deparse(method)

  for (i in 1:length(parms)) {
    thisparmname = names(parms)[i]
    cmd = sprintf("%s, %s=parms$%s", cmd, thisparmname, thisparmname)
  }
  cmd = sprintf("%s)", cmd)
  rv = eval(parse(text=cmd))
  return(rv)
}


#-----------------------------------------------------------------------------------------------------------------------
#   H2O Server Health & Info
#-----------------------------------------------------------------------------------------------------------------------

#' Determine if an H2O cluster is up or not
#'
#' @param conn H2O connection object
#' @return TRUE if the cluster is up; FALSE otherwise
h2o.clusterIsUp <- function(conn) {
  if (missing(conn)) conn <- .retrieveH2O(parent.frame())
  if (class(conn) != "h2o.client") stop("client must be a h2o.client object")

  rv = h2o.doRawGET(conn = conn, urlSuffix = "")

  if (rv$curlError) {
    return(FALSE)
  }
  if (rv$httpStatusCode != 200) {
    return(FALSE)
  }

  return(TRUE)
}

#' Print H2O cluster info
#'
#' @param conn H2O connection object
h2o.clusterInfo <- function(conn) {
  if(missing(conn)) conn <- .retrieveH2O(parent.frame())
  stopifnot(class(conn) == "h2o.client")
  if(! h2o.clusterIsUp(conn)) {
    ip = conn@ip
    port = conn@port
    message = sprintf("Cannot connect to H2O instance at http://%s:%d", ip, port)
    stop(message)
  }

  res = NULL
  {
    res <- fromJSON(h2o.doSafeGET(conn = conn, urlSuffix = .h2o.__CLOUD))
    nodeInfo <- res$nodes
    numCPU <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

    if (numCPU == 0) {
      # If the cloud has not been up for a few seconds yet, then query again.
      # Sometimes the heartbeat info with cores and memory has not had a chance
      # to post its information yet.
      threeSeconds = 3
      Sys.sleep(threeSeconds)
      res <- fromJSON(h2o.doSafeGET(conn = conn, urlSuffix = .h2o.__CLOUD))
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

#' Check H2O Server Health
#'
#' Warn if there are sick nodes.
.h2o.__checkClientHealth <- function(conn) {
  grabCloudStatus <- function(conn) {
    rv = h2o.doGET(conn = conn, urlSuffix = .h2o.__CLOUD)

    if (rv$curlError) {
      ip = conn@ip
      port = conn@port
      warning(rv$curlErrorMessage)
      message = sprintf("H2O connection has been severed.  Cannot connect to instance at http://%s:%d", ip, port)
      stop(message)
    }

    if (rv$httpStatusCode != 200) {
      ip = conn@ip
      port = conn@port
      message = sprintf("H2O returned HTTP status %d (%s)", rv$httpStatusCode, rv$httpStatusMessage)
      warning(message)
      message = sprintf("H2O connection has been severed.  Instance unhealthy at http://%s:%d", ip, port)
      stop(message)
    }

    tmp = fromJSON(rv$payload)
    return(tmp)
  }

  checker <- function(node, conn) {
    status <- as.logical(node$healthy)
    elapsed <- as.integer(as.POSIXct(Sys.time()))*1000 - node$last_ping
    nport <- unlist(strsplit(node$h2o$node, ":"))[2]
    if(!status) .h2o.__cloudSick(node_name = NULL, conn = conn)
    if(elapsed > 60000) .h2o.__cloudSick(node_name = NULL, conn = conn)
    if(elapsed > 10000) {
        Sys.sleep(5)
        invisible(lapply(grabCloudStatus(conn)$nodes, checker, conn))
    }
    return(0)
  }

  cloudStatus <- grabCloudStatus(conn)
  if(cloudStatus$bad_nodes != 0) .h2o.__cloudSick(node_name = NULL, conn = conn)
  lapply(cloudStatus$nodes, checker, conn)
  return(0)
}

#' Helper method to issue a warning.
.h2o.__cloudSick <- function(node_name = NULL, conn) {
  url <- .h2o.calcBaseURL(conn = conn, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = .h2o.__CLOUD)
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
    pb <- txtProgressBar(style = 3)
    tryCatch(while((prog <- .h2o.__poll(client, job_key))$prog != 1 && !prog$DONE) { Sys.sleep(pollInterval); setTxtProgressBar(pb, prog$prog) },
             error = function(e) { cat("\nPolling fails:\n"); print(e) },
             finally = setTxtProgressBar(pb, 1.0))
    if (!prog$DONE) {
      tryCatch(while(!(prog <- .h2o.__poll(client, job_key))$DONE) { Sys.sleep(pollInterval/2) },
               error = function(e) { cat("\nPolling fails:\n"); print(e) })
    }
    close(pb)
  } else
    tryCatch(while(prog<- .h2o.__poll(client, job_key) != -1 && !prog$DONE) { Sys.sleep(pollInterval) },
             finally = .h2o.__cancelJob(client, job_key))
}

#'
#' Return the progress so far and check if job is done
.h2o.__poll <- function(client, keyName) {
  if(missing(client)) stop("client is missing!")
  if(class(client) != "h2o.client") stop("client must be a h2o.client object")
  if(missing(keyName)) stop("keyName is missing!")
  if(!is.character(keyName) || nchar(keyName) == 0) stop("keyName must be a non-empty string")

  page <- 'Jobs.json/' %p0% keyName
  res <- .h2o.__remoteSend(client, page)

  res <- res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog <- list(prog = numeric(0), DONE = FALSE)
  jobRes <- NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key$name == keyName)
      jobRes <- res[[i]]
  }
  if(is.null(jobRes)) stop("Job key ", keyName, " not found in job queue")
  if(!is.null(jobRes$status) && jobRes$status == "CANCELLED") stop("Job key ", keyName, " was cancelled by user")
  else if(!is.null(jobRes$exception) && jobRes$exception == 1) stop(jobRes$status)
  prog$prog <- jobRes$progress
  if (jobRes$status == "DONE") prog$DONE <- TRUE
  prog
}

#'
#' Cancel a job.
.h2o.__cancelJob <- function(client, keyName) {
  res = .h2o.__remoteSend(client, .h2o.__JOBS)
  res = res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog = NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key$name == keyName) {
      prog = res[[i]]; break
    }
  }
  if(is.null(prog)) stop("Job key ", keyName, " not found in job queue")
#  if(!(prog$cancelled || prog$progress == -1.0 || prog$progress == -2.0 || prog$end_time == -1)) {
##    .h2o.__remoteSend(client, .h2o.__PAGE_CANCEL, key=keyName)
#    cat("Job key", keyName, "was cancelled by user\n")
#  }
  cat("Job key", keyName, "was cancelled by user\n")
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

#------------------------------------ Utilities ------------------------------------#
.h2o.__writeToFile <- function(res, fileName) {
  formatVector <- function(vec) {
    result <- rep(" ", length(vec))
    nams <- names(vec)
    for(i in 1:length(vec))
      result[i] = paste(nams[i], ": ", vec[i], sep="")
    paste(result, collapse="\n")
  }

  cat("Writing JSON response to", fileName, "\n")
  temp <- strsplit(as.character(Sys.time()), " ")[[1]]
  write(paste(temp[1], temp[2], '\t', formatVector(unlist(res))), file = fileName, append = TRUE)
}

.h2o.__checkForFactors <- function(object) {
  if(class(object) != "h2o.frame") return(FALSE)
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

.uri.exists <- function(uri) url.exists(uri)

.h2o.__formatError <- function(error,prefix="  ") {
  result = ""
  items = strsplit(as.character(error),"\n")[[1]];
  for (i in 1:length(items))
    result = paste(result,prefix,items[i],"\n",sep="")
  result
}
